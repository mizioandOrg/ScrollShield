import { useState, useEffect, useRef, useCallback } from "react";

const FEED_ITEMS = [
  { id: 1, type: "content", creator: "@chef_maria", text: "Easy 5-min pasta hack you NEED to try", category: "cooking", engagement: "2.1M views", color: "#1a1a2e" },
  { id: 2, type: "ad", creator: "@glossybeauty", text: "✨ Get 40% off our new serum! Link in bio", brand: "GlossyBeauty", category: "beauty", engagement: "Sponsored", color: "#16213e" },
  { id: 3, type: "content", creator: "@space_nerd42", text: "Why Jupiter's red spot is shrinking 🪐", category: "science", engagement: "890K views", color: "#0f3460" },
  { id: 4, type: "content", creator: "@diy_dan", text: "I built a bookshelf from pallets in 2 hours", category: "DIY", engagement: "1.4M views", color: "#1a1a2e" },
  { id: 5, type: "ad", creator: "@cryptomax_io", text: "🚀 Turn $100 into $10K — Start trading NOW", brand: "CryptoMax", category: "finance", engagement: "Sponsored", color: "#16213e" },
  { id: 6, type: "content", creator: "@yoga_with_lex", text: "Morning stretch for people who hate mornings", category: "fitness", engagement: "3.2M views", color: "#1a1a2e" },
  { id: 7, type: "ad", creator: "@skinfix_official", text: "Dermatologists are SHOCKED by this cream", brand: "SkinFix", category: "beauty", engagement: "Sponsored", color: "#0f3460" },
  { id: 8, type: "content", creator: "@historybuff", text: "The Roman emperor who made his horse a senator", category: "history", engagement: "5.7M views", color: "#1a1a2e" },
  { id: 9, type: "content", creator: "@beatmaker_99", text: "Made this beat with sounds from my kitchen", category: "music", engagement: "670K views", color: "#16213e" },
  { id: 10, type: "ad", creator: "@betking_sport", text: "💰 Bet €5 get €50 FREE — Champions League special", brand: "BetKing", category: "gambling", engagement: "Sponsored", color: "#1a1a2e" },
  { id: 11, type: "content", creator: "@plant_daddy", text: "Your monstera is dying because of THIS mistake", category: "plants", engagement: "2.8M views", color: "#0f3460" },
  { id: 12, type: "ad", creator: "@slimtea_fit", text: "Lose 10kg in 2 weeks with SlimTea™ 🍵", brand: "SlimTea", category: "diet", engagement: "Sponsored", color: "#16213e" },
  { id: 13, type: "content", creator: "@codewithsam", text: "Build an app in 60 seconds with AI", category: "tech", engagement: "4.1M views", color: "#1a1a2e" },
  { id: 14, type: "content", creator: "@dog_rescue_heroes", text: "We found him alone in the rain... wait for it 🥹", category: "animals", engagement: "12M views", color: "#16213e" },
  { id: 15, type: "ad", creator: "@fashionnova", text: "NEW DROP 🔥 Shop the look — 60% off everything", brand: "FashionNova", category: "fashion", engagement: "Sponsored", color: "#0f3460" },
  { id: 16, type: "content", creator: "@mathwhiz", text: "This math trick will blow your teacher's mind", category: "education", engagement: "1.9M views", color: "#1a1a2e" },
  { id: 17, type: "ad", creator: "@vpn_ultra", text: "Your ISP is watching you. Protect yourself NOW.", brand: "VPNUltra", category: "tech", engagement: "Sponsored", color: "#16213e" },
  { id: 18, type: "content", creator: "@streetfood_tokyo", text: "This $3 ramen stall has a 2-hour line", category: "food", engagement: "8.4M views", color: "#1a1a2e" },
  { id: 19, type: "content", creator: "@skatepark_life", text: "First kickflip at 47 years old 🛹", category: "sports", engagement: "6.2M views", color: "#0f3460" },
  { id: 20, type: "ad", creator: "@ai_course_pro", text: "Learn AI in 30 days — $0 with code TIKTOK", brand: "AiCoursePro", category: "education", engagement: "Sponsored", color: "#1a1a2e" },
];

const USER_PREFERENCES = {
  interests: ["science", "cooking", "tech", "music", "education", "DIY"],
  blocked: ["gambling", "diet", "finance"],
};

function scoreItem(item, prefs) {
  if (item.type === "ad") return -100;
  if (prefs.blocked.includes(item.category)) return -50;
  if (prefs.interests.includes(item.category)) return 10;
  return 0;
}

// Floating ad counter pill
function AdCounter({ count, total, sessionMinutes }) {
  const revenue = (count * 0.018).toFixed(2);
  return (
    <div style={{
      position: "absolute", top: 12, left: "50%", transform: "translateX(-50%)",
      background: count > 0 ? "rgba(255,50,50,0.15)" : "rgba(255,255,255,0.08)",
      backdropFilter: "blur(16px)", WebkitBackdropFilter: "blur(16px)",
      border: count > 0 ? "1px solid rgba(255,80,80,0.4)" : "1px solid rgba(255,255,255,0.12)",
      borderRadius: 100, padding: "6px 16px",
      display: "flex", alignItems: "center", gap: 10, zIndex: 100,
      transition: "all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)",
      fontFamily: "'JetBrains Mono', 'SF Mono', monospace", fontSize: 11,
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
        <div style={{
          width: 7, height: 7, borderRadius: "50%",
          background: count > 5 ? "#ff4444" : count > 0 ? "#ffaa00" : "#44ff44",
          boxShadow: count > 5 ? "0 0 8px #ff4444" : "none",
          animation: count > 5 ? "pulse 1s infinite" : "none",
        }} />
        <span style={{ color: "#fff", fontWeight: 700, letterSpacing: 0.5 }}>
          {count} ads
        </span>
      </div>
      <div style={{ width: 1, height: 14, background: "rgba(255,255,255,0.15)" }} />
      <span style={{ color: "rgba(255,255,255,0.5)", fontSize: 10 }}>
        ~${revenue} sold
      </span>
      <div style={{ width: 1, height: 14, background: "rgba(255,255,255,0.15)" }} />
      <span style={{ color: "rgba(255,255,255,0.5)", fontSize: 10 }}>
        {sessionMinutes}m session
      </span>
    </div>
  );
}

// Single feed card
function FeedCard({ item, isAd, isBlocked, isBuffering, index }) {
  const adFlag = item.type === "ad";
  return (
    <div style={{
      background: adFlag
        ? "linear-gradient(135deg, #1c0a0a 0%, #2a0f0f 100%)"
        : `linear-gradient(135deg, ${item.color} 0%, #0a0a1a 100%)`,
      borderRadius: 16, padding: "20px 18px", minHeight: 120,
      border: adFlag ? "1px solid rgba(255,60,60,0.25)" : "1px solid rgba(255,255,255,0.06)",
      position: "relative", overflow: "hidden",
      opacity: isBuffering ? 0.3 : isBlocked ? 0.15 : 1,
      transform: isBuffering ? "scale(0.96)" : isBlocked ? "scale(0.94)" : "scale(1)",
      transition: "all 0.5s cubic-bezier(0.25, 1, 0.5, 1)",
      filter: isBuffering ? "blur(2px)" : isBlocked ? "blur(4px) grayscale(1)" : "none",
    }}>
      {adFlag && (
        <div style={{
          position: "absolute", top: 10, right: 10,
          background: "rgba(255,50,50,0.2)", border: "1px solid rgba(255,50,50,0.4)",
          borderRadius: 6, padding: "2px 8px",
          fontSize: 9, fontWeight: 700, color: "#ff6b6b",
          letterSpacing: 1.5, textTransform: "uppercase",
          fontFamily: "'JetBrains Mono', monospace",
        }}>
          AD
        </div>
      )}
      {isBlocked && (
        <div style={{
          position: "absolute", top: "50%", left: "50%",
          transform: "translate(-50%, -50%)",
          background: "rgba(0,0,0,0.7)", borderRadius: 8, padding: "6px 14px",
          fontSize: 10, color: "#ff6b6b", fontWeight: 600,
          fontFamily: "'JetBrains Mono', monospace", letterSpacing: 1,
          border: "1px solid rgba(255,60,60,0.3)",
        }}>
          FILTERED
        </div>
      )}
      {isBuffering && (
        <div style={{
          position: "absolute", top: "50%", left: "50%",
          transform: "translate(-50%, -50%)",
          fontSize: 10, color: "rgba(100,200,255,0.6)", fontWeight: 600,
          fontFamily: "'JetBrains Mono', monospace", letterSpacing: 1,
        }}>
          SCANNING...
        </div>
      )}
      <div style={{
        fontSize: 13, fontWeight: 700, color: "#fff",
        marginBottom: 6, lineHeight: 1.4,
        fontFamily: "'Space Grotesk', 'Satoshi', sans-serif",
      }}>
        {item.text}
      </div>
      <div style={{
        display: "flex", justifyContent: "space-between", alignItems: "center",
        marginTop: 10,
      }}>
        <span style={{
          fontSize: 11, color: adFlag ? "#ff8888" : "rgba(255,255,255,0.45)",
          fontFamily: "'JetBrains Mono', monospace",
        }}>
          {item.creator}
        </span>
        <span style={{
          fontSize: 10,
          color: adFlag ? "#ff6b6b" : "rgba(255,255,255,0.3)",
          fontFamily: "'JetBrains Mono', monospace",
          fontWeight: adFlag ? 600 : 400,
        }}>
          {item.engagement}
        </span>
      </div>
      {adFlag && item.brand && (
        <div style={{
          marginTop: 8, fontSize: 10, color: "rgba(255,100,100,0.5)",
          fontFamily: "'JetBrains Mono', monospace",
        }}>
          Advertiser: {item.brand} · Category: {item.category}
        </div>
      )}
    </div>
  );
}

// Session summary panel
function SessionSummary({ adCount, totalSeen, brands, categories, minutes }) {
  const adRate = totalSeen > 0 ? ((adCount / totalSeen) * 100).toFixed(0) : 0;
  return (
    <div style={{
      background: "rgba(255,255,255,0.03)", borderRadius: 16,
      border: "1px solid rgba(255,255,255,0.08)", padding: "18px 16px",
      marginTop: 12,
    }}>
      <div style={{
        fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.4)",
        letterSpacing: 2, textTransform: "uppercase", marginBottom: 14,
        fontFamily: "'JetBrains Mono', monospace",
      }}>
        Session Report
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        {[
          { label: "Ads served", value: adCount, color: "#ff6b6b" },
          { label: "Ad rate", value: `${adRate}%`, color: "#ffaa44" },
          { label: "Brands", value: brands.length, color: "#44aaff" },
          { label: "Est. revenue", value: `$${(adCount * 0.018).toFixed(2)}`, color: "#ff6b6b" },
        ].map((s, i) => (
          <div key={i} style={{
            background: "rgba(255,255,255,0.03)", borderRadius: 10, padding: "10px 12px",
            border: "1px solid rgba(255,255,255,0.05)",
          }}>
            <div style={{
              fontSize: 20, fontWeight: 800, color: s.color,
              fontFamily: "'JetBrains Mono', monospace",
            }}>
              {s.value}
            </div>
            <div style={{
              fontSize: 9, color: "rgba(255,255,255,0.35)", marginTop: 2,
              fontFamily: "'JetBrains Mono', monospace", letterSpacing: 0.5,
              textTransform: "uppercase",
            }}>
              {s.label}
            </div>
          </div>
        ))}
      </div>
      {brands.length > 0 && (
        <div style={{ marginTop: 14 }}>
          <div style={{
            fontSize: 9, color: "rgba(255,255,255,0.3)", marginBottom: 6,
            fontFamily: "'JetBrains Mono', monospace", letterSpacing: 1,
            textTransform: "uppercase",
          }}>
            Advertisers targeting you
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
            {brands.map((b, i) => (
              <span key={i} style={{
                background: "rgba(255,60,60,0.1)", border: "1px solid rgba(255,60,60,0.2)",
                borderRadius: 6, padding: "3px 8px", fontSize: 10, color: "#ff8888",
                fontFamily: "'JetBrains Mono', monospace",
              }}>
                {b}
              </span>
            ))}
          </div>
        </div>
      )}
      {categories.length > 0 && (
        <div style={{ marginTop: 10 }}>
          <div style={{
            fontSize: 9, color: "rgba(255,255,255,0.3)", marginBottom: 6,
            fontFamily: "'JetBrains Mono', monospace", letterSpacing: 1,
            textTransform: "uppercase",
          }}>
            Ad categories
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
            {categories.map((c, i) => (
              <span key={i} style={{
                background: "rgba(255,170,0,0.1)", border: "1px solid rgba(255,170,0,0.2)",
                borderRadius: 6, padding: "3px 8px", fontSize: 10, color: "#ffcc66",
                fontFamily: "'JetBrains Mono', monospace",
              }}>
                {c}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default function ScrollShield() {
  const [mode, setMode] = useState("off"); // off, counter, mask
  const [feedIndex, setFeedIndex] = useState(0);
  const [adsSeen, setAdsSeen] = useState(0);
  const [seenItems, setSeenItems] = useState([]);
  const [brands, setBrands] = useState([]);
  const [adCategories, setAdCategories] = useState([]);
  const [sessionTime, setSessionTime] = useState(0);
  const [bufferPhase, setBufferPhase] = useState(false);
  const [bufferQueue, setBufferQueue] = useState([]);
  const [filteredFeed, setFilteredFeed] = useState([]);
  const [maskStats, setMaskStats] = useState({ scanned: 0, blocked: 0, reordered: 0 });
  const intervalRef = useRef(null);

  // Session timer
  useEffect(() => {
    intervalRef.current = setInterval(() => {
      setSessionTime(t => t + 1);
    }, 60000);
    return () => clearInterval(intervalRef.current);
  }, []);

  // Reset on mode change
  useEffect(() => {
    setFeedIndex(0);
    setAdsSeen(0);
    setSeenItems([]);
    setBrands([]);
    setAdCategories([]);
    setSessionTime(0);
    setBufferPhase(false);
    setBufferQueue([]);
    setFilteredFeed([]);
    setMaskStats({ scanned: 0, blocked: 0, reordered: 0 });
  }, [mode]);

  const scroll = useCallback(() => {
    if (mode === "mask") {
      // Buffer phase: grab next 8 items, filter and sort
      setBufferPhase(true);
      const startIdx = feedIndex;
      const batch = [];
      for (let i = 0; i < 8; i++) {
        batch.push(FEED_ITEMS[(startIdx + i) % FEED_ITEMS.length]);
      }
      setBufferQueue(batch);

      setTimeout(() => {
        const scored = batch
          .map(item => ({ item, score: scoreItem(item, USER_PREFERENCES) }))
          .sort((a, b) => b.score - a.score);
        
        const passed = scored.filter(s => s.score > -50).map(s => s.item);
        const blocked = scored.filter(s => s.score <= -50).length;
        const adsInBatch = batch.filter(b => b.type === "ad").length;

        setFilteredFeed(prev => [...prev, ...passed]);
        setMaskStats(prev => ({
          scanned: prev.scanned + batch.length,
          blocked: prev.blocked + blocked,
          reordered: prev.reordered + (passed.length > 1 ? passed.length : 0),
        }));
        setAdsSeen(prev => prev + adsInBatch);
        setBrands(prev => {
          const newBrands = batch.filter(b => b.type === "ad" && b.brand).map(b => b.brand);
          return [...new Set([...prev, ...newBrands])];
        });
        setAdCategories(prev => {
          const newCats = batch.filter(b => b.type === "ad").map(b => b.category);
          return [...new Set([...prev, ...newCats])];
        });
        setFeedIndex(prev => (prev + 8) % FEED_ITEMS.length);
        setBufferPhase(false);
        setBufferQueue([]);
      }, 1800);
    } else {
      // Normal or counter mode
      const nextIndex = (feedIndex + 1) % FEED_ITEMS.length;
      const item = FEED_ITEMS[nextIndex];
      setSeenItems(prev => [...prev, item]);
      if (item.type === "ad") {
        setAdsSeen(prev => prev + 1);
        if (item.brand) setBrands(prev => [...new Set([...prev, item.brand])]);
        setAdCategories(prev => [...new Set([...prev, item.category])]);
      }
      setFeedIndex(nextIndex);
    }
  }, [mode, feedIndex]);

  const currentItem = FEED_ITEMS[feedIndex];

  return (
    <div style={{
      minHeight: "100vh",
      background: "linear-gradient(180deg, #07070f 0%, #0d0d1a 50%, #07070f 100%)",
      fontFamily: "'Space Grotesk', 'Satoshi', -apple-system, sans-serif",
      color: "#fff",
      display: "flex", flexDirection: "column", alignItems: "center",
      padding: "24px 16px",
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600;700;800&display=swap');
        @keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.4; } }
        @keyframes scanline {
          0% { transform: translateY(-100%); }
          100% { transform: translateY(400%); }
        }
        @keyframes shimmer {
          0% { background-position: -200% 0; }
          100% { background-position: 200% 0; }
        }
        * { box-sizing: border-box; }
      `}</style>

      {/* Header */}
      <div style={{ textAlign: "center", marginBottom: 28, maxWidth: 400 }}>
        <div style={{
          fontSize: 11, letterSpacing: 4, color: "rgba(255,255,255,0.25)",
          fontFamily: "'JetBrains Mono', monospace", fontWeight: 600,
          textTransform: "uppercase", marginBottom: 8,
        }}>
          OS-Level Prototype
        </div>
        <h1 style={{
          fontSize: 28, fontWeight: 800, margin: 0, lineHeight: 1.1,
          background: "linear-gradient(135deg, #fff 0%, #88aaff 50%, #ff6688 100%)",
          WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
          letterSpacing: -0.5,
        }}>
          ScrollShield
        </h1>
        <p style={{
          fontSize: 12, color: "rgba(255,255,255,0.35)", marginTop: 6,
          lineHeight: 1.5, fontFamily: "'JetBrains Mono', monospace",
        }}>
          Your phone's defense layer against algorithmic manipulation
        </p>
      </div>

      {/* Mode Selector */}
      <div style={{
        display: "flex", gap: 6, marginBottom: 24,
        background: "rgba(255,255,255,0.04)", borderRadius: 12, padding: 4,
        border: "1px solid rgba(255,255,255,0.06)",
      }}>
        {[
          { key: "off", label: "Shield Off" },
          { key: "counter", label: "Ad Counter" },
          { key: "mask", label: "Scroll Mask" },
        ].map(m => (
          <button key={m.key} onClick={() => setMode(m.key)} style={{
            background: mode === m.key
              ? m.key === "off" ? "rgba(255,255,255,0.1)"
                : m.key === "counter" ? "rgba(255,80,80,0.15)"
                : "rgba(80,150,255,0.15)"
              : "transparent",
            border: mode === m.key
              ? m.key === "off" ? "1px solid rgba(255,255,255,0.15)"
                : m.key === "counter" ? "1px solid rgba(255,80,80,0.3)"
                : "1px solid rgba(80,150,255,0.3)"
              : "1px solid transparent",
            borderRadius: 8, padding: "8px 16px", cursor: "pointer",
            color: mode === m.key ? "#fff" : "rgba(255,255,255,0.4)",
            fontSize: 11, fontWeight: 600,
            fontFamily: "'JetBrains Mono', monospace",
            letterSpacing: 0.5,
            transition: "all 0.3s ease",
          }}>
            {m.label}
          </button>
        ))}
      </div>

      {/* Phone mockup */}
      <div style={{
        width: 320, minHeight: 540,
        background: "#0a0a14",
        borderRadius: 32, padding: 2,
        border: "2px solid rgba(255,255,255,0.08)",
        position: "relative", overflow: "hidden",
        boxShadow: "0 20px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(255,255,255,0.03)",
      }}>
        {/* Notch */}
        <div style={{
          position: "absolute", top: 0, left: "50%", transform: "translateX(-50%)",
          width: 100, height: 22, background: "#0a0a14",
          borderBottomLeftRadius: 14, borderBottomRightRadius: 14, zIndex: 50,
        }} />

        {/* Ad counter overlay */}
        {mode === "counter" && (
          <AdCounter count={adsSeen} total={seenItems.length} sessionMinutes={sessionTime} />
        )}

        {/* Mask scanning overlay */}
        {mode === "mask" && bufferPhase && (
          <div style={{
            position: "absolute", inset: 0, zIndex: 90,
            background: "rgba(0,10,30,0.85)",
            backdropFilter: "blur(8px)", WebkitBackdropFilter: "blur(8px)",
            display: "flex", flexDirection: "column",
            alignItems: "center", justifyContent: "center",
            borderRadius: 30,
          }}>
            <div style={{
              position: "absolute", inset: 0, overflow: "hidden", borderRadius: 30,
            }}>
              <div style={{
                position: "absolute", left: 0, right: 0, height: 2,
                background: "linear-gradient(90deg, transparent, rgba(80,150,255,0.6), transparent)",
                animation: "scanline 1.2s linear infinite",
              }} />
            </div>
            <div style={{
              fontFamily: "'JetBrains Mono', monospace",
              fontSize: 12, fontWeight: 700, color: "rgba(100,180,255,0.8)",
              letterSpacing: 3, textTransform: "uppercase", marginBottom: 12,
            }}>
              Scanning Feed
            </div>
            <div style={{
              fontFamily: "'JetBrains Mono', monospace",
              fontSize: 10, color: "rgba(255,255,255,0.3)",
              textAlign: "center", lineHeight: 1.8,
            }}>
              Pre-scrolling 8 items...<br />
              Filtering ads & sorting by preference
            </div>
            <div style={{
              marginTop: 16, display: "flex", gap: 6,
            }}>
              {bufferQueue.map((item, i) => (
                <div key={i} style={{
                  width: 8, height: 8, borderRadius: "50%",
                  background: item.type === "ad" ? "#ff4444" : "#44ff88",
                  opacity: 0.6,
                  animation: `pulse ${0.6 + i * 0.1}s infinite`,
                }} />
              ))}
            </div>
          </div>
        )}

        {/* Mask shield indicator */}
        {mode === "mask" && !bufferPhase && (
          <div style={{
            position: "absolute", top: 12, left: "50%", transform: "translateX(-50%)",
            background: "rgba(40,100,255,0.12)",
            backdropFilter: "blur(16px)", WebkitBackdropFilter: "blur(16px)",
            border: "1px solid rgba(80,150,255,0.3)",
            borderRadius: 100, padding: "5px 14px",
            display: "flex", alignItems: "center", gap: 8, zIndex: 100,
            fontFamily: "'JetBrains Mono', monospace", fontSize: 10,
          }}>
            <div style={{
              width: 6, height: 6, borderRadius: "50%",
              background: "#44aaff", boxShadow: "0 0 8px #44aaff",
            }} />
            <span style={{ color: "#88ccff", fontWeight: 600 }}>
              Shield Active
            </span>
            <div style={{ width: 1, height: 12, background: "rgba(255,255,255,0.1)" }} />
            <span style={{ color: "rgba(255,255,255,0.4)" }}>
              {maskStats.blocked} blocked
            </span>
          </div>
        )}

        {/* Feed content area */}
        <div style={{
          padding: "40px 14px 14px",
          display: "flex", flexDirection: "column", gap: 10,
          minHeight: 500,
        }}>
          {mode === "mask" ? (
            filteredFeed.length > 0 ? (
              filteredFeed.slice(-5).map((item, i) => (
                <FeedCard key={`mask-${i}`} item={item} isAd={false} isBlocked={false} />
              ))
            ) : (
              <div style={{
                textAlign: "center", padding: "80px 20px",
                color: "rgba(255,255,255,0.25)",
                fontFamily: "'JetBrains Mono', monospace", fontSize: 11,
              }}>
                Tap "Scroll" to activate the mask buffer
              </div>
            )
          ) : (
            <>
              <FeedCard item={currentItem} isAd={currentItem.type === "ad"} />
              {seenItems.slice(-3).reverse().map((item, i) => (
                <FeedCard
                  key={`seen-${i}`} item={item}
                  isAd={item.type === "ad"}
                  style={{ opacity: 0.5 - i * 0.15 }}
                />
              ))}
            </>
          )}
        </di>

        {/* Scroll button */}
        <div style={{ padding: "0 14px 16px" }}>
          <button
            onClick={scroll}
            disabled={bufferPhase}
            style={{
              width: "100%", padding: "14px",
              background: bufferPhase
                ? "rgba(255,255,255,0.03)"
                : mode === "mask"
                  ? "linear-gradient(135deg, rgba(40,100,255,0.2), rgba(80,60,255,0.2))"
                  : "linear-gradient(135deg, rgba(255,255,255,0.08), rgba(255,255,255,0.04))",
              border: bufferPhase
                ? "1px solid rgba(255,255,255,0.05)"
                : mode === "mask"
                  ? "1px solid rgba(80,150,255,0.3)"
                  : "1px solid rgba(255,255,255,0.1)",
              borderRadius: 12, cursor: bufferPhase ? "wait" : "pointer",
              color: bufferPhase ? "rgba(255,255,255,0.2)" : "#fff",
              fontSize: 13, fontWeight: 700,
              fontFamily: "'JetBrains Mono', monospace",
              letterSpacing: 1,
              transition: "all 0.3s ease",
            }}
          >
            {bufferPhase ? "Scanning..." : mode === "mask" ? "↓ Scroll (Masked)" : "↓ Scroll"}
          </button>
        </div>
      </div>

      {/* Stats panel below phone */}
      {(mode === "counter" && adsSeen > 0) && (
        <div style={{ width: 320, marginTop: 8 }}>
          <SessionSummary
            adCount={adsSeen}
            totalSeen={seenItems.length}
            brands={brands}
            categories={adCategories}
            minutes={sessionTime}
          />
        </div>
      )}

      {mode === "mask" && maskStats.scanned > 0 && (
        <div style={{
          width: 320, marginTop: 8,
          background: "rgba(40,100,255,0.05)", borderRadius: 16,
          border: "1px solid rgba(80,150,255,0.15)", padding: "18px 16px",
        }}>
          <div style={{
            fontSize: 11, fontWeight: 700, color: "rgba(100,180,255,0.5)",
            letterSpacing: 2, textTransform: "uppercase", marginBottom: 14,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            Mask Report
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
            {[
              { label: "Scanned", value: maskStats.scanned, color: "#88ccff" },
              { label: "Blocked", value: maskStats.blocked, color: "#ff6b6b" },
              { label: "Passed", value: maskStats.scanned - maskStats.blocked, color: "#44ff88" },
            ].map((s, i) => (
              <div key={i} style={{
                background: "rgba(255,255,255,0.03)", borderRadius: 10, padding: "10px",
                textAlign: "center", border: "1px solid rgba(255,255,255,0.05)",
              }}>
                <div style={{
                  fontSize: 22, fontWeight: 800, color: s.color,
                  fontFamily: "'JetBrains Mono', monospace",
                }}>
                  {s.value}
                </div>
                <div style={{
                  fontSize: 8, color: "rgba(255,255,255,0.3)", marginTop: 2,
                  fontFamily: "'JetBrains Mono', monospace", letterSpacing: 0.5,
                  textTransform: "uppercase",
                }}>
                  {s.label}
                </div>
              </div>
            ))}
          </div>
          {brands.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <div style={{
                fontSize: 9, color: "rgba(255,255,255,0.25)", marginBottom: 6,
                fontFamily: "'JetBrains Mono', monospace", letterSpacing: 1,
                textTransform: "uppercase",
              }}>
                Intercepted advertisers
              </div>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                {brands.map((b, i) => (
                  <span key={i} style={{
                    background: "rgba(255,60,60,0.08)", border: "1px solid rgba(255,60,60,0.15)",
                    borderRadius: 6, padding: "3px 8px", fontSize: 10, color: "#ff8888",
                    fontFamily: "'JetBrains Mono', monospace",
                    textDecoration: "line-through", textDecorationColor: "rgba(255,60,60,0.4)",
                  }}>
                    {b}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Explanation */}
      <div style={{
        maxWidth: 340, marginTop: 24, padding: "0 8px",
        textAlign: "center",
      }}>
        {mode === "off" && (
          <p style={{
            fontSize: 11, color: "rgba(255,255,255,0.25)", lineHeight: 1.7,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            No protection active. The platform's algorithm has full control over what you see and when. Ads blend seamlessly into your feed.
          </p>
        )}
        {mode === "counter" && (
          <p style={{
            fontSize: 11, color: "rgba(255,255,255,0.25)", lineHeight: 1.7,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            The Ad Counter makes the invisible visible. Every promoted post increments the counter. Scroll and watch how quickly ads accumulate — and how much your attention is worth.
          </p>
        )}
        {mode === "mask" && (
          <p style={{
            fontSize: 11, color: "rgba(255,255,255,0.25)", lineHeight: 1.7,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            The Scroll Mask pre-scrolls 8 items before you see anything. It filters ads, removes blocked categories (gambling, diet, finance), and re-ranks by your interests. You never touch the raw feed.
          </p>
        )}
      </div>
    </div>
  );
}
