#!/usr/bin/env python3
"""Deterministic text-classifier dataset generator (seed 20260515).

Produces three JSONL files:
- text_train.jsonl: 2000 synthetic captions for training
- text_eval.jsonl: 200 records (byte-identical to test resource text_feed_200.jsonl)
- text_child_safety.jsonl: 50 records (byte-identical to text_child_safety_50.jsonl)

The eval and child_safety files are also intended to be copied to
app/src/test/resources/dataset/ and app/src/androidTest/assets/dataset/.
"""
from __future__ import annotations

import json
import os
import random
import sys
from pathlib import Path

SEED = 20260515
LABELS = [
    "ORGANIC", "OFFICIAL_AD", "INFLUENCER_PROMO",
    "ENGAGEMENT_BAIT", "OUTRAGE_TRIGGER", "EDUCATIONAL", "UNKNOWN",
]
TOPICS = [
    "COMEDY", "MUSIC", "FOOD", "SPORTS", "FASHION", "TECH", "EDUCATION",
    "GAMING", "FINANCE", "POLITICS", "ANIMALS", "TRAVEL", "ART", "NEWS",
    "RELATIONSHIPS", "CARS", "HOME", "PARENTING", "HEALTH", "NATURE",
]
APPS = [
    "com.zhiliaoapp.musically",
    "com.instagram.android",
    "com.google.android.youtube",
]

# Bag-of-words templates: deterministic by label index
_TEMPLATES = {
    "ORGANIC": [
        "just sharing my morning {topic} routine with you",
        "loving this {topic} content from my feed today",
        "everyday {topic} vibes for everyone",
        "a quiet moment with {topic} and friends",
        "this {topic} reminded me of childhood",
    ],
    "OFFICIAL_AD": [
        "Sponsored. Try our new {topic} product today",
        "Ad: limited time {topic} offer free shipping",
        "Promoted by BrandX get {topic} discount now",
        "Sponsored partnership brings you premium {topic}",
        "Buy now: {topic} sale ends midnight",
    ],
    "INFLUENCER_PROMO": [
        "use my code SAVE20 for {topic} gear",
        "link in bio for the {topic} bundle I love",
        "swipe up to grab the {topic} deal I use daily",
        "this {topic} brand sent me a gift code",
        "honestly the best {topic} I have tried",
    ],
    "ENGAGEMENT_BAIT": [
        "you won't believe what happened in this {topic} video",
        "tag a friend who needs this {topic} hack",
        "double tap if you love {topic} like I do",
        "watch till the end for the {topic} twist",
        "comment YES if you want more {topic}",
    ],
    "OUTRAGE_TRIGGER": [
        "this {topic} scandal is destroying lives",
        "they don't want you to know about this {topic} truth",
        "I'm furious about what they did to {topic}",
        "the shocking {topic} story everyone is hiding",
        "wake up people {topic} is a lie",
    ],
    "EDUCATIONAL": [
        "today I learned about {topic} from a textbook",
        "let's break down how {topic} actually works",
        "a short explainer on the science of {topic}",
        "key facts about {topic} you should know",
        "this {topic} concept comes from research",
    ],
    "UNKNOWN": [
        "{topic}",
        "x {topic}",
        ".",
        "{topic}!",
        "??",
    ],
}

# Topic → list of evocative words for caption (label-agnostic)
_TOPIC_WORDS = {
    "COMEDY": "comedy", "MUSIC": "music", "FOOD": "food", "SPORTS": "sports",
    "FASHION": "fashion", "TECH": "tech", "EDUCATION": "education",
    "GAMING": "gaming", "FINANCE": "finance", "POLITICS": "politics",
    "ANIMALS": "animals", "TRAVEL": "travel", "ART": "art", "NEWS": "news",
    "RELATIONSHIPS": "relationships", "CARS": "cars", "HOME": "home",
    "PARENTING": "parenting", "HEALTH": "health", "NATURE": "nature",
}


def _topic_vector(topic_idx: int) -> list:
    v = [0.0] * 20
    v[topic_idx] = 1.0
    return v


def _record(rid: str, seed: int, app: str, creator: str, label: str,
            topic: str, *, child_unsafe: bool = False) -> dict:
    rng = random.Random(seed)
    template = rng.choice(_TEMPLATES[label])
    caption = template.format(topic=_TOPIC_WORDS[topic])
    hashtag_pool = ["#fyp", "#viral", "#trending", "#" + _TOPIC_WORDS[topic]]
    n_tags = rng.randint(0, 3)
    hashtags = rng.sample(hashtag_pool, n_tags)
    rec = {
        "id": rid,
        "seed": seed,
        "app": app,
        "creator": creator,
        "caption": caption,
        "hashtags": hashtags,
        "label": label,
        "expectedClassification": label,
        "expectedTopic": topic,
        # Training fields:
        "classification": label,
        "topic_vector": _topic_vector(TOPICS.index(topic)),
    }
    if child_unsafe:
        rec["childUnsafe"] = True
    return rec


def write_feed_200(path: Path) -> None:
    """120 ORGANIC / 40 OFFICIAL_AD / 20 INFLUENCER_PROMO / 20 ENGAGEMENT_BAIT."""
    rng = random.Random(SEED)
    distribution = (
        [("ORGANIC", 120)]
        + [("OFFICIAL_AD", 40)]
        + [("INFLUENCER_PROMO", 20)]
        + [("ENGAGEMENT_BAIT", 20)]
    )
    records = []
    counter = 0
    for label, count in distribution:
        for _ in range(count):
            counter += 1
            topic = rng.choice(TOPICS)
            app = rng.choice(APPS)
            seed = SEED + counter
            creator = f"creator_{counter:04d}"
            rid = f"feed_{counter:04d}"
            records.append(_record(rid, seed, app, creator, label, topic))
    with path.open("w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, sort_keys=True, ensure_ascii=False) + "\n")


def write_child_safety_50(path: Path) -> None:
    """20 gambling (ENGAGEMENT_BAIT, FINANCE),
       15 diet (OUTRAGE_TRIGGER, HEALTH),
       15 outrage (OUTRAGE_TRIGGER, POLITICS)."""
    rng = random.Random(SEED + 999)
    distribution = (
        [("ENGAGEMENT_BAIT", "FINANCE", 20, "gambling")]
        + [("OUTRAGE_TRIGGER", "HEALTH", 15, "diet")]
        + [("OUTRAGE_TRIGGER", "POLITICS", 15, "outrage")]
    )
    records = []
    counter = 0
    for label, topic, count, _kind in distribution:
        for _ in range(count):
            counter += 1
            app = rng.choice(APPS)
            seed = SEED + 100000 + counter
            creator = f"unsafe_{counter:03d}"
            rid = f"unsafe_{counter:03d}"
            records.append(_record(rid, seed, app, creator, label, topic, child_unsafe=True))
    with path.open("w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, sort_keys=True, ensure_ascii=False) + "\n")


def write_train_2000(path: Path) -> None:
    """2000 deterministic synthetic captions across all 7 labels & all topics."""
    rng = random.Random(SEED + 7)
    records = []
    for i in range(2000):
        # cycle labels deterministically
        label = LABELS[i % len(LABELS)]
        topic = TOPICS[i % len(TOPICS)]
        app = APPS[i % len(APPS)]
        seed = SEED + 200000 + i
        creator = f"train_{i:05d}"
        rid = f"train_{i:05d}"
        records.append(_record(rid, seed, app, creator, label, topic))
    rng.shuffle(records)
    with path.open("w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, sort_keys=True, ensure_ascii=False) + "\n")


def main() -> int:
    base = Path(__file__).resolve().parent
    feed_200 = base / "text_eval.jsonl"
    child_50 = base / "text_child_safety.jsonl"
    train_2000 = base / "text_train.jsonl"

    write_feed_200(feed_200)
    write_child_safety_50(child_50)
    write_train_2000(train_2000)

    print(f"[generate_text_data] wrote {feed_200} ({feed_200.stat().st_size} bytes)")
    print(f"[generate_text_data] wrote {child_50} ({child_50.stat().st_size} bytes)")
    print(f"[generate_text_data] wrote {train_2000} ({train_2000.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
