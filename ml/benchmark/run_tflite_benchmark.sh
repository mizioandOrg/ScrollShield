#!/usr/bin/env bash
# ============================================================================
# ScrollShield2 WI-18 — TFLite on-device benchmark driver
# ============================================================================
#
# Purpose
# -------
# Dual-mode benchmark harness for the WI-17 MobileNetV3-Small visual model.
#
#   1. NATIVE MODE (default): pushes the upstream TFLite `benchmark_model`
#      binary to a connected device and reports inference timings under the
#      requested delegate (nnapi | gpu | xnnpack | cpu).
#
#   2. CI MODE (--ci): drives the WI-18 androidx.benchmark microbenchmarks
#      via `./gradlew :app:connectedBenchmarkAndroidTest`, scrapes logcat
#      for harvested baselines, compares them against the in-script reference
#      gate values, and emits a JSON report + non-zero exit on regression.
#
# Harvested log line formats (CI mode)
# ------------------------------------
#   WI15_BENCH_RESULT metric=<name> median_ms=<int> p95_ms=<int> delegate=<str>
#   WI15_BENCH_RESULT metric=mem_<name> peak_mb=<int> delegate=<str>
#   WI15_BENCH_RESULT metric=mem_leak_drift drift_mb=<int>
#   WI15_BENCH_RESULT metric=thermal_drift_pct value=<int> trigger_iter=<int>
#   WI14_THERMAL_FEEDBACK event=thermal_summary total_iters=50 \
#       throttle_trigger_iter=<int> peak_temp_c=<int> start_p95_ms=<int> \
#       end_p95_ms=<int> drift_pct=<int>
#
# Reference baselines (gate values)
# ---------------------------------
#   cold_start              p95 <= 350 ms
#   nnapi_inference         p95 <= 60  ms
#   cpu_xnnpack_inference   p95 <= 100 ms
#   frame_capture           p95 <= 15  ms
#   crop_resize             p95 <= 8   ms
#   e2e_tier1               p95 <= 80  ms
#   mem_prescan             peak <= 150 MB
#   mem_live                peak <= 150 MB
#   mem_both_models         peak <= 150 MB
#   mem_leak_drift          drift <= 5 MB
#   thermal_drift_pct       value <= 25 %
#
# Exit codes
# ----------
#   0  pass
#   1  usage error
#   2  infra error (missing tool / build / connect failure)
#   3  regression detected (one or more baselines breached)
#   4  thermal early throttle (trigger_iter < 30, unless --no-thermal-fail)
#
# CI invocation
# -------------
#   ml/benchmark/run_tflite_benchmark.sh \
#       --ci \
#       --device "$ANDROID_SERIAL" \
#       --model app/src/main/assets/visual_classifier.tflite \
#       --delegate nnapi --threads 4 --iterations 50 --warmup 5 \
#       --output build/wi18-bench.json
#
# JSON output schema
# ------------------
# {
#   "device":        "<adb serial / Build.MODEL>",
#   "delegate":      "<nnapi|gpu|xnnpack|cpu>",
#   "threads":       <int>,
#   "iterations":    <int>,
#   "tflite_native": { "median_us": <int|null>, "stddev_us": <int|null> },
#   "microbench":    [ { "metric": "<name>", "median_ms": <int>,
#                        "p95_ms": <int>, "delegate": "<str>" }, ... ],
#   "memory":        [ { "metric": "mem_*", "peak_mb": <int>,
#                        "delegate": "<str>" }, ... ],
#   "thermal":       { "total_iters": 50, "throttle_trigger_iter": <int>,
#                      "peak_temp_c": <int>, "start_p95_ms": <int>,
#                      "end_p95_ms": <int>, "drift_pct": <int> },
#   "regressions":   [ "<metric>=<value> exceeds <threshold>", ... ],
#   "exit_code":     <int>
# }
#
# Portability: pure bash + adb + awk + grep + sed + jq. No GNU-only flags.
# ============================================================================

set -euo pipefail

# ------------------------------ defaults ------------------------------------

MODEL=""
DEVICE="${ANDROID_SERIAL:-}"
DELEGATE="nnapi"
THREADS=4
ITERATIONS=50
WARMUP=5
OUTPUT="build/wi18-bench.json"
CI_MODE=0
NO_THERMAL_FAIL=0
BENCH_BINARY_REMOTE="/data/local/tmp/benchmark_model"
LOGCAT_DUMP="/tmp/wi18-logcat.txt"

# ------------------------------ helpers -------------------------------------

die() {
    local code="${2:-1}"
    echo "ERROR: $1" >&2
    exit "$code"
}

require_cmd() {
    for c in "$@"; do
        command -v "$c" >/dev/null 2>&1 || die "missing required command: $c" 2
    done
}

usage() {
    sed -n '1,90p' "$0"
    exit "${1:-0}"
}

on_err() {
    local rc=$?
    cat >&2 <<EOF
{"error":"benchmark harness aborted","exit_code":${rc},"line":${BASH_LINENO[0]:-0}}
EOF
}
trap on_err ERR

# ------------------------------ arg parsing ---------------------------------

while [[ $# -gt 0 ]]; do
    case "$1" in
        --model)            MODEL="${2:-}"; shift 2 ;;
        --device)           DEVICE="${2:-}"; shift 2 ;;
        --delegate)         DELEGATE="${2:-}"; shift 2 ;;
        --threads)          THREADS="${2:-}"; shift 2 ;;
        --iterations)       ITERATIONS="${2:-}"; shift 2 ;;
        --warmup)           WARMUP="${2:-}"; shift 2 ;;
        --output)           OUTPUT="${2:-}"; shift 2 ;;
        --ci)               CI_MODE=1; shift ;;
        --no-thermal-fail)  NO_THERMAL_FAIL=1; shift ;;
        -h|--help)          usage 0 ;;
        *)                  echo "Unknown flag: $1" >&2; usage 1 ;;
    esac
done

case "$DELEGATE" in
    nnapi|gpu|xnnpack|cpu) ;;
    *) die "--delegate must be one of: nnapi|gpu|xnnpack|cpu" 1 ;;
esac

require_cmd adb jq awk grep sed

# ------------------------------ device check --------------------------------

ADB=(adb)
if [[ -n "$DEVICE" ]]; then
    ADB=(adb -s "$DEVICE")
fi

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
    die "no device reachable via adb (DEVICE='$DEVICE')" 2
fi

mkdir -p "$(dirname "$OUTPUT")"

# ------------------------------ TFLite native -------------------------------

run_native_tflite() {
    local median_us="null"
    local stddev_us="null"

    if [[ -z "$MODEL" || ! -f "$MODEL" ]]; then
        echo "WARN: --model not provided or missing; skipping native TFLite run" >&2
        echo "$median_us $stddev_us"
        return 0
    fi

    if ! "${ADB[@]}" shell test -x "$BENCH_BINARY_REMOTE" 2>/dev/null; then
        echo "WARN: $BENCH_BINARY_REMOTE not installed on device; skipping native run" >&2
        echo "$median_us $stddev_us"
        return 0
    fi

    local remote_model="/data/local/tmp/visual_classifier.tflite"
    "${ADB[@]}" push "$MODEL" "$remote_model" >/dev/null

    local use_nnapi="false" use_gpu="false" use_xnn="false"
    case "$DELEGATE" in
        nnapi)   use_nnapi="true" ;;
        gpu)     use_gpu="true" ;;
        xnnpack) use_xnn="true" ;;
        cpu)     ;;
    esac

    local raw
    raw=$("${ADB[@]}" shell "$BENCH_BINARY_REMOTE" \
        --graph="$remote_model" \
        --num_threads="$THREADS" \
        --num_runs="$ITERATIONS" \
        --warmup_runs="$WARMUP" \
        --use_nnapi="$use_nnapi" \
        --use_gpu="$use_gpu" \
        --use_xnnpack="$use_xnn" 2>&1 || true)

    # Example line: "Inference timings in us: Init: 1234, First inference: 5678,
    # Warmup (avg): 234, Inference (avg): 4567"
    local line
    line=$(echo "$raw" | grep -E "Inference timings in us" | tail -n 1 || true)
    if [[ -n "$line" ]]; then
        median_us=$(echo "$line" | awk -F'Inference \\(avg\\): ' '{print $2}' | awk -F'[, ]' '{print $1}')
        stddev_us=$(echo "$raw" | grep -E "Inference \\(std\\)" | awk -F': ' '{print $2}' | awk '{print $1}' | head -n 1)
        [[ -z "$median_us" ]] && median_us="null"
        [[ -z "$stddev_us" ]] && stddev_us="null"
    fi

    echo "$median_us $stddev_us"
}

# ------------------------------ CI mode -------------------------------------

run_ci_microbench() {
    pushd "$(dirname "$0")/../.." >/dev/null

    echo "[wi18] assembling AndroidTest APK..." >&2
    if ! ./gradlew :app:assembleAndroidTest -q; then
        popd >/dev/null
        die "gradle assembleAndroidTest failed" 2
    fi

    "${ADB[@]}" logcat -c
    echo "[wi18] running connectedBenchmarkAndroidTest..." >&2
    if ! ./gradlew :app:connectedBenchmarkAndroidTest -PandroidTestSerial="$DEVICE" -q; then
        popd >/dev/null
        die "connectedBenchmarkAndroidTest failed" 2
    fi
    popd >/dev/null

    "${ADB[@]}" logcat -d -s WI15_BENCH_RESULT:I WI14_THERMAL_FEEDBACK:W > "$LOGCAT_DUMP" 2>/dev/null \
        || die "logcat dump failed" 2
}

# ------------------------------ parsing -------------------------------------

declare -A BASELINE_P95=(
    [cold_start]=350
    [nnapi_inference]=60
    [cpu_xnnpack_inference]=100
    [frame_capture]=15
    [crop_resize]=8
    [e2e_tier1]=80
)
declare -A BASELINE_MEM=(
    [mem_prescan]=150
    [mem_live]=150
    [mem_both_models]=150
)
BASELINE_LEAK=5
BASELINE_THERMAL=25

parse_kv() {
    local line="$1" key="$2"
    echo "$line" | awk -v k="$key" '{
        for (i = 1; i <= NF; i++) {
            n = split($i, kv, "=")
            if (n == 2 && kv[1] == k) { print kv[2]; exit }
        }
    }'
}

build_json() {
    local native_median="$1" native_stddev="$2"
    local microbench_json="$3" memory_json="$4" thermal_json="$5"
    local regressions_json="$6" exit_code="$7"

    jq -n \
        --arg device "${DEVICE:-unknown}" \
        --arg delegate "$DELEGATE" \
        --argjson threads "$THREADS" \
        --argjson iterations "$ITERATIONS" \
        --argjson native_median "$native_median" \
        --argjson native_stddev "$native_stddev" \
        --argjson microbench "$microbench_json" \
        --argjson memory "$memory_json" \
        --argjson thermal "$thermal_json" \
        --argjson regressions "$regressions_json" \
        --argjson exit_code "$exit_code" \
        '{
            device: $device,
            delegate: $delegate,
            threads: $threads,
            iterations: $iterations,
            tflite_native: { median_us: $native_median, stddev_us: $native_stddev },
            microbench: $microbench,
            memory: $memory,
            thermal: $thermal,
            regressions: $regressions,
            exit_code: $exit_code
        }'
}

# ------------------------------ main ----------------------------------------

REGRESSIONS=()
MICRO_JSON="[]"
MEM_JSON="[]"
THERMAL_JSON="null"
EXIT_CODE=0
THERMAL_TRIGGER_ITER=999

read -r NATIVE_MED NATIVE_STD < <(run_native_tflite || echo "null null")
[[ -z "${NATIVE_MED:-}" ]] && NATIVE_MED="null"
[[ -z "${NATIVE_STD:-}" ]] && NATIVE_STD="null"

if [[ "$CI_MODE" -eq 1 ]]; then
    run_ci_microbench

    # ---- microbench latency baselines ----
    micro_entries="[]"
    while IFS= read -r line; do
        metric="$(parse_kv "$line" metric)"
        med="$(parse_kv "$line" median_ms)"
        p95="$(parse_kv "$line" p95_ms)"
        deleg="$(parse_kv "$line" delegate)"
        [[ -z "$metric" || -z "$med" || -z "$p95" ]] && continue
        entry=$(jq -n \
            --arg m "$metric" --argjson md "$med" \
            --argjson p "$p95" --arg d "${deleg:-unknown}" \
            '{metric:$m, median_ms:$md, p95_ms:$p, delegate:$d}')
        micro_entries=$(echo "$micro_entries" | jq --argjson e "$entry" '. + [$e]')
        thr="${BASELINE_P95[$metric]:-}"
        if [[ -n "$thr" && "$p95" -gt "$thr" ]]; then
            REGRESSIONS+=("$metric p95=${p95}ms exceeds ${thr}ms")
        fi
    done < <(grep "WI15_BENCH_RESULT" "$LOGCAT_DUMP" | grep "median_ms=" || true)
    MICRO_JSON="$micro_entries"

    # ---- memory peak baselines ----
    mem_entries="[]"
    while IFS= read -r line; do
        metric="$(parse_kv "$line" metric)"
        peak="$(parse_kv "$line" peak_mb)"
        deleg="$(parse_kv "$line" delegate)"
        [[ -z "$metric" || -z "$peak" ]] && continue
        entry=$(jq -n \
            --arg m "$metric" --argjson p "$peak" --arg d "${deleg:-unknown}" \
            '{metric:$m, peak_mb:$p, delegate:$d}')
        mem_entries=$(echo "$mem_entries" | jq --argjson e "$entry" '. + [$e]')
        thr="${BASELINE_MEM[$metric]:-}"
        if [[ -n "$thr" && "$peak" -gt "$thr" ]]; then
            REGRESSIONS+=("$metric peak=${peak}MB exceeds ${thr}MB")
        fi
    done < <(grep "WI15_BENCH_RESULT" "$LOGCAT_DUMP" | grep "peak_mb=" || true)
    MEM_JSON="$mem_entries"

    # ---- leak drift ----
    while IFS= read -r line; do
        drift="$(parse_kv "$line" drift_mb)"
        [[ -z "$drift" ]] && continue
        if [[ "$drift" -gt "$BASELINE_LEAK" ]]; then
            REGRESSIONS+=("mem_leak_drift drift=${drift}MB exceeds ${BASELINE_LEAK}MB")
        fi
    done < <(grep "metric=mem_leak_drift" "$LOGCAT_DUMP" || true)

    # ---- thermal summary ----
    therm_line=$(grep "event=thermal_summary" "$LOGCAT_DUMP" | tail -n 1 || true)
    if [[ -n "$therm_line" ]]; then
        ti="$(parse_kv "$therm_line" total_iters)"
        tt="$(parse_kv "$therm_line" throttle_trigger_iter)"
        pt="$(parse_kv "$therm_line" peak_temp_c)"
        sp="$(parse_kv "$therm_line" start_p95_ms)"
        ep="$(parse_kv "$therm_line" end_p95_ms)"
        dp="$(parse_kv "$therm_line" drift_pct)"
        THERMAL_JSON=$(jq -n \
            --argjson ti "${ti:-0}" --argjson tt "${tt:--1}" \
            --argjson pt "${pt:--1}" --argjson sp "${sp:-0}" \
            --argjson ep "${ep:-0}" --argjson dp "${dp:-0}" \
            '{total_iters:$ti, throttle_trigger_iter:$tt, peak_temp_c:$pt,
              start_p95_ms:$sp, end_p95_ms:$ep, drift_pct:$dp}')
        if [[ -n "${dp:-}" && "$dp" -gt "$BASELINE_THERMAL" ]]; then
            REGRESSIONS+=("thermal_drift_pct=${dp}% exceeds ${BASELINE_THERMAL}%")
        fi
        if [[ -n "${tt:-}" && "$tt" -ge 0 ]]; then
            THERMAL_TRIGGER_ITER="$tt"
        fi
    fi

    if [[ ${#REGRESSIONS[@]} -gt 0 ]]; then
        EXIT_CODE=3
    fi
    if [[ "$THERMAL_TRIGGER_ITER" -lt 30 && "$NO_THERMAL_FAIL" -eq 0 ]]; then
        EXIT_CODE=4
    fi
fi

REG_JSON=$(printf '%s\n' "${REGRESSIONS[@]:-}" \
    | jq -R -s 'split("\n") | map(select(length > 0))')

build_json "$NATIVE_MED" "$NATIVE_STD" "$MICRO_JSON" "$MEM_JSON" \
    "$THERMAL_JSON" "$REG_JSON" "$EXIT_CODE" > "$OUTPUT"

echo "[wi18] report written to: $OUTPUT (exit=$EXIT_CODE)" >&2
exit "$EXIT_CODE"
