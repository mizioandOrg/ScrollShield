package com.scrollshield.reports

import com.scrollshield.data.model.MonthlyAggregate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stable, documented serialization for weekly / child / monthly reports.
 *
 * Field order is part of the export contract: external tooling depending on column
 * positions should remain stable across releases.
 */
object ReportExporter {

    // ---- Weekly ----

    /**
     * Weekly report JSON. Top-level keys (stable order):
     *   periodStartMs, periodEndMs, adsDetected, adsSkipped, averageSatisfaction,
     *   totalTimeByProfileMinutes, topFiveBrands, adToContentRatioTrend,
     *   tierBreakdown, classificationCounts.
     */
    fun weeklyJson(report: WeeklyReport): String {
        val obj = JSONObject()
        obj.put("periodStartMs", report.periodStartMs)
        obj.put("periodEndMs", report.periodEndMs)
        obj.put("adsDetected", report.adsDetected)
        obj.put("adsSkipped", report.adsSkipped)
        if (report.averageSatisfaction != null) {
            obj.put("averageSatisfaction", report.averageSatisfaction.toDouble())
        } else {
            obj.put("averageSatisfaction", JSONObject.NULL)
        }
        val timeObj = JSONObject()
        for ((profileId, mins) in report.totalTimeByProfileMinutes.toSortedMap()) {
            timeObj.put(profileId, mins.toDouble())
        }
        obj.put("totalTimeByProfileMinutes", timeObj)
        val brandsArr = JSONArray()
        for ((brand, count) in report.topFiveBrands) {
            brandsArr.put(JSONObject().apply {
                put("brand", brand)
                put("count", count)
            })
        }
        obj.put("topFiveBrands", brandsArr)
        val trend = JSONArray()
        for (v in report.adToContentRatioTrend) trend.put(v.toDouble())
        obj.put("adToContentRatioTrend", trend)
        val tier = JSONObject().apply {
            put("tier0", report.tierBreakdown.tier0)
            put("tier1", report.tierBreakdown.tier1)
            put("tier2", report.tierBreakdown.tier2)
        }
        obj.put("tierBreakdown", tier)
        val cc = JSONObject()
        for ((k, v) in report.classificationCounts.toSortedMap(compareBy { it.name })) cc.put(k.name, v)
        obj.put("classificationCounts", cc)
        return obj.toString()
    }

    /**
     * Weekly CSV. Header (stable):
     * `periodStartMs,periodEndMs,profileId,totalTimeMinutes,adsDetected,adsSkipped,top5Brands,avgSatisfaction,tier0,tier1,tier2,classificationCountsJson`
     *
     * One data row per profileId in `totalTimeByProfileMinutes`, plus a
     * `__total__` summary row when more than one profile is present.
     */
    fun weeklyCsv(report: WeeklyReport): String {
        val sb = StringBuilder()
        sb.append("periodStartMs,periodEndMs,profileId,totalTimeMinutes,adsDetected,adsSkipped,top5Brands,avgSatisfaction,tier0,tier1,tier2,classificationCountsJson")
        sb.append('\n')
        val brandsStr = report.topFiveBrands.joinToString(";") { "${it.first}:${it.second}" }
        val ccJson = JSONObject().apply {
            for ((k, v) in report.classificationCounts.toSortedMap(compareBy { it.name })) put(k.name, v)
        }.toString()
        val avgStr = report.averageSatisfaction?.toString() ?: ""
        for ((profileId, mins) in report.totalTimeByProfileMinutes.toSortedMap()) {
            sb.append(report.periodStartMs).append(',')
            sb.append(report.periodEndMs).append(',')
            sb.append(csvEscape(profileId)).append(',')
            sb.append(mins).append(',')
            sb.append(report.adsDetected).append(',')
            sb.append(report.adsSkipped).append(',')
            sb.append(csvEscape(brandsStr)).append(',')
            sb.append(avgStr).append(',')
            sb.append(report.tierBreakdown.tier0).append(',')
            sb.append(report.tierBreakdown.tier1).append(',')
            sb.append(report.tierBreakdown.tier2).append(',')
            sb.append(csvEscape(ccJson))
            sb.append('\n')
        }
        if (report.totalTimeByProfileMinutes.isEmpty()) {
            sb.append(report.periodStartMs).append(',')
            sb.append(report.periodEndMs).append(',')
            sb.append("__total__,0.0,")
            sb.append(report.adsDetected).append(',')
            sb.append(report.adsSkipped).append(',')
            sb.append(csvEscape(brandsStr)).append(',')
            sb.append(avgStr).append(',')
            sb.append(report.tierBreakdown.tier0).append(',')
            sb.append(report.tierBreakdown.tier1).append(',')
            sb.append(report.tierBreakdown.tier2).append(',')
            sb.append(csvEscape(ccJson))
            sb.append('\n')
        }
        return sb.toString()
    }

    // ---- Child ----

    fun childJson(report: ChildActivityReport): String {
        val obj = JSONObject()
        obj.put("periodStartMs", report.periodStartMs)
        obj.put("periodEndMs", report.periodEndMs)
        obj.put("adsBlocked", report.adsBlocked)
        val timeObj = JSONObject()
        for ((app, mins) in report.timePerAppMinutes.toSortedMap()) {
            timeObj.put(app, mins.toDouble())
        }
        obj.put("timePerAppMinutes", timeObj)
        obj.put("categoriesEncountered", JSONArray(report.categoriesEncountered))
        val budget = JSONObject()
        for ((app, row) in report.budgetCompliance.toSortedMap()) {
            budget.put(app, JSONObject().apply {
                put("budgetMinutes", row.budgetMinutes)
                put("actualMinutes", row.actualMinutes.toDouble())
                put("withinBudget", row.withinBudget)
            })
        }
        obj.put("budgetCompliance", budget)
        return obj.toString()
    }

    fun childCsv(report: ChildActivityReport): String {
        val sb = StringBuilder()
        sb.append("# categories: ").append(report.categoriesEncountered.joinToString(";"))
        sb.append('\n')
        sb.append("periodStartMs,periodEndMs,app,timeMinutes,adsBlockedShare,budgetMinutes,withinBudget")
        sb.append('\n')
        val totalAdsBlocked = report.adsBlocked
        val apps = (report.timePerAppMinutes.keys + report.budgetCompliance.keys).toSortedSet()
        for (app in apps) {
            val mins = report.timePerAppMinutes[app] ?: 0f
            val row = report.budgetCompliance[app]
            val budgetMinutes = row?.budgetMinutes ?: 0
            val withinBudget = row?.withinBudget ?: true
            sb.append(report.periodStartMs).append(',')
            sb.append(report.periodEndMs).append(',')
            sb.append(csvEscape(app)).append(',')
            sb.append(mins).append(',')
            sb.append(totalAdsBlocked).append(',')
            sb.append(budgetMinutes).append(',')
            sb.append(withinBudget)
            sb.append('\n')
        }
        return sb.toString()
    }

    // ---- Monthly ----

    /**
     * Monthly aggregate JSON. Concatenates stored JSON sub-objects in stable key order.
     */
    fun monthlyJson(aggregate: MonthlyAggregate): String {
        val obj = JSONObject()
        obj.put("id", aggregate.id)
        obj.put("yearMonth", aggregate.yearMonth)
        obj.put("periodStartMs", aggregate.periodStartMs)
        obj.put("periodEndMs", aggregate.periodEndMs)
        obj.put("totalSessions", aggregate.totalSessions)
        obj.put("totalDurationMinutes", aggregate.totalDurationMinutes.toDouble())
        obj.put("totalAdsDetected", aggregate.totalAdsDetected)
        obj.put("totalAdsSkipped", aggregate.totalAdsSkipped)
        if (aggregate.averageSatisfaction != null) {
            obj.put("averageSatisfaction", aggregate.averageSatisfaction.toDouble())
        } else {
            obj.put("averageSatisfaction", JSONObject.NULL)
        }
        obj.put("perAppBreakdown", safeJsonObject(aggregate.perAppBreakdownJson))
        obj.put("topTenBrands", safeJsonArray(aggregate.topTenBrandsJson))
        obj.put("tierDistribution", safeJsonObject(aggregate.tierDistributionJson))
        obj.put("visualClassifierFeedback", safeJsonObject(aggregate.visualClassifierFeedbackJson))
        obj.put("computedAt", aggregate.computedAt)
        return obj.toString()
    }

    private fun safeJsonObject(s: String): Any =
        try { JSONObject(s) } catch (_: Exception) { JSONObject() }

    private fun safeJsonArray(s: String): Any =
        try { JSONArray(s) } catch (_: Exception) { JSONArray() }

    private fun csvEscape(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuote) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
