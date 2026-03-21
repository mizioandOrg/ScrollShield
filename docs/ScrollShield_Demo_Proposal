**PRODUCT PROPOSAL**

**ScrollShield**

OS-Level Defence Against Algorithmic Manipulation

A proposal for an operating system feature that intercepts, analyses,
and refilters social media content feeds in real time --- giving users
sovereign control over the algorithms that compete for their attention.

  -------------------- ----------------------------------------------------
  **Document**         Demo Proposal v1.0

  **Date**             March 2026

  **Classification**   Confidential --- For Evaluation Only

  **Status**           Concept & Prototype Stage
  -------------------- ----------------------------------------------------

1\. Executive Summary

ScrollShield is a proposed operating-system-level feature that places an
intelligent defence layer between the user and any attention-maximising
application. It intercepts content feeds delivered by platforms such as
TikTok, Instagram Reels, and YouTube Shorts, then refilters and re-ranks
that content according to the user's own stated preferences --- before a
single frame reaches the screen.

The proposal includes two complementary capabilities designed to work
independently or together:

-   Ad Counter --- A persistent, floating overlay that counts every
    promoted post served to the user in real time, tracks estimated
    advertising revenue generated from their attention, and presents a
    session summary showing which brands and categories are targeting
    them.

-   Scroll Mask --- A buffered content proxy that pre-scrolls 5--10
    items ahead of the user, classifies each item (organic content,
    advertisement, or flagged category), filters out unwanted material,
    and re-ranks the remaining content according to a user-defined
    preference model before rendering it on screen.

Together, these features transform the smartphone from a passive
delivery mechanism for algorithmic manipulation into an active agent
working on behalf of the user. The ad counter creates awareness; the
scroll mask creates protection.

2\. Problem Statement

2.1 The Attention Economy

The global digital advertising market, fuelled by captured human
attention, is projected to exceed \$1 trillion by 2030. Social media
platforms generate revenue by maximising time-on-app: every additional
minute a user scrolls is another unit of ad inventory sold. The
recommendation algorithms powering platforms like TikTok, Instagram, and
YouTube are explicitly optimised for engagement metrics --- predicted
likes, predicted watch time, predicted shares --- none of which
correlate with user wellbeing.

TikTok's parent company ByteDance alone generated an estimated \$155
billion in revenue in 2024, with the recommendation algorithm valued as
the single most important asset in the business. When the U.S.
government mandated a divestiture, the algorithm's licensing deal became
the centrepiece of negotiations, with one analyst noting that buying
TikTok without the algorithm would be "like buying a Ferrari without the
engine."

2.2 The User Has No Leverage

Currently, the algorithm lives inside each app. The user has two
choices: accept the algorithm, or don't use the app. There is no
mechanism for a user to modify, override, or even observe how the
algorithm is selecting content for them. Screen-time tools (Apple's
Screen Time, Google's Digital Wellbeing) count minutes but do not
understand content. They treat a 30-minute session of educational
content identically to a 30-minute session of engagement-bait outrage
--- a blunt instrument for a nuanced problem.

2.3 The Scale of Impact

Average daily TikTok usage is 45.8 minutes per user. A typical scrolling
session includes 15--20 advertisements. TikTok's estimated annual carbon
footprint is 50 million tonnes of CO₂ --- comparable to the total
emissions of Greece --- driven largely by video streaming sustained by
the recommendation algorithm. Research shows that TikTok can build a
reasonably accurate psychological preference model for a new user in
approximately 30--40 minutes and as few as 200 interactions.

3\. Proposed Solution

3.1 Architecture Overview

ScrollShield operates as an OS-level middleware layer positioned between
the application and the user interface. It does not modify the app,
block network traffic, or require jailbreaking. Instead, it leverages
accessibility APIs, on-device machine learning, and content
classification to observe, analyse, and optionally refilter what the app
displays.

  ---------------- --------------------------- ---------------------------
  **Layer**        **Function**                **Technology**

  **Intent         User defines preferences,   On-device preference store,
  Profile**        blocked categories, time    onboarding flow
                   budgets, and personal       
                   objective function          

  **Feed           OS monitors content as it   Accessibility APIs,
  Interception**   is rendered by the app,     on-device vision models,
                   classifying each item in    Neural Engine
                   real time                   

  **Re-ranking**   Filters ads and blocked     Lightweight transformer
                   content, re-orders          model, user preference
                   remaining items by user     embeddings
                   preference model            

  **Feedback       Post-session satisfaction   Reinforcement learning from
  Loop**           surveys refine the          human feedback (on-device)
                   preference model over time  
  ---------------- --------------------------- ---------------------------

3.2 Feature One: Ad Counter

The ad counter is a persistent floating overlay displayed whenever a
doomscrolling application is active. It provides:

-   Real-time count of promoted/sponsored posts encountered during the
    session

-   Estimated advertising revenue generated from the user's attention
    (based on published CPM averages for the platform and demographic)

-   Session duration tracking

-   Session summary report listing all brands encountered, ad
    categories, ad-to-content ratio, and cumulative statistics

The ad counter operates passively --- it does not block or modify any
content. Its function is purely informational, relying on the
well-established psychological principle that awareness of consumption
changes behaviour. This is the same mechanism that makes calorie labels
effective: simply counting creates friction.

3.3 Feature Two: Scroll Mask

The scroll mask is an active intervention layer. When the user opens a
doomscrolling application, the mask activates and creates a buffered
intermediary:

1.  The mask silently pre-scrolls 5--10 items from the app's feed,
    consuming them before the user sees anything.

2.  Each pre-scrolled item is classified using on-device ML: organic
    content, advertisement, influencer promotion, or flagged category.

3.  Items matching the user's blocked categories (e.g., gambling, diet
    products, political outrage) are filtered out.

4.  Remaining items are re-ranked according to the user's preference
    model: interests are boosted, low-relevance content is
    deprioritised.

5.  The filtered, re-ranked mini-feed is presented to the user. A visual
    scanning animation signals that the mask is active.

From the platform's perspective, the app is being used normally --- the
mask generates real scroll events, real impressions, and real dwell
time. But the human user only sees content that has passed through their
personal filter. The platform's algorithm still runs, but its output is
intercepted and overridden.

4\. Technical Feasibility

4.1 On-Device ML Classification

Modern smartphones include dedicated neural processing hardware (Apple
Neural Engine, Qualcomm Hexagon NPU) capable of running lightweight
classification models with sub-50ms latency. TikTok's own system
operates within a 50ms inference budget per recommendation;
ScrollShield's classification task is simpler (binary ad/not-ad
classification plus category tagging) and can comfortably run within the
same time envelope.

4.2 Ad Detection Methods

The system uses three complementary detection methods:

-   Label detection: Identifying the "Sponsored" or "Ad" label that
    platforms are legally required to display in most jurisdictions.
    This is a simple OCR/accessibility-API task with near-100% accuracy
    for officially labelled ads.

-   Content analysis: On-device vision and language models identify
    product placements, brand logos, discount codes, and call-to-action
    patterns in unlabelled influencer promotions.

-   Signature matching: A continuously updated database of known ad
    creatives (built by the doomscrolling agent system described in
    Section 6) enables fingerprint-based matching for rapid
    identification.

4.3 Platform Compatibility

ScrollShield is designed to work with any vertically-scrolling content
feed. Initial target platforms include TikTok, Instagram Reels, YouTube
Shorts, and X (formerly Twitter). The accessibility-API approach means
no modification to the target app is required, and the system is
resilient to app updates since it operates at the rendering layer, not
the network layer.

5\. Market Opportunity

5.1 Market Context

The digital wellness market is embedded within the broader global
wellness industry, which is valued at \$6.3 trillion and projected to
reach \$9 trillion by 2028. The product recommendation engine market
alone stands at \$9.15 billion in 2025, growing at 33% annually. There
is significant and growing consumer demand for tools that address
algorithmic manipulation.

5.2 Competitive Landscape

  ---------------- -------------------- ---------------- -----------------
  **Solution**     **Approach**         **Limitation**   **ScrollShield
                                                         Advantage**

  Apple Screen     Counts minutes,      No content       Qualitative
  Time             locks apps after     awareness        understanding of
                   time limit                            feed content

  Google Digital   Usage dashboards,    No feed          Active filtering
  Wellbeing        bedtime mode         interception     and re-ranking

  Minimalist       Remove apps entirely Removes          Keeps
  phones                                functionality    functionality,
                                                         adds intelligence

  Web ad blockers  Block web ads via    Don't work in    Works at OS level
                   browser              native apps      across all apps
  ---------------- -------------------- ---------------- -----------------

5.3 Strategic Positioning

The ideal deployment path is integration into a major mobile operating
system. Apple is the strongest candidate: its business model is hardware
sales (not advertising), it has precedent for user-protection features
that disrupt ad revenue (App Tracking Transparency cost Meta an
estimated \$10 billion), and "Algorithmic Sovereignty" aligns with
Apple's established privacy branding. An alternative path is a
standalone Android application (leveraging Android's accessibility
service APIs) or a custom Android fork.

6\. Companion System: Doomscrolling Agent Fleet

To maximise ScrollShield's effectiveness, we propose a companion
intelligence system: an autonomous agent fleet that doomscrolls TikTok
and other platforms 24/7 on behalf of the project, building a
comprehensive, real-time advertising intelligence database.

6.1 Architecture

-   Persona farm: Hundreds of synthetic accounts spanning demographic
    profiles (age, gender, location, interests), each behaving as a
    convincing human user.

-   Autonomous scrolling engine: Each agent operates a headless browser
    or device-farm instance, continuously scrolling the For You page and
    recording every item encountered.

-   Ad detection pipeline: Multi-modal classification (label detection,
    content analysis, API payload inspection) identifies and catalogues
    every advertisement.

-   Open database: All detected ads are stored with rich metadata ---
    advertiser, product, creative, targeting demographic, frequency,
    time-of-day, and geographic targeting.

-   Signature feed: The database continuously generates ad fingerprints
    that are pushed to ScrollShield clients, enabling rapid
    signature-based ad blocking on-device.

6.2 Intelligence Value

The agent fleet answers questions that are currently unanswerable
without insider access: which ads target teenagers vs. adults, which
advertisers target users consuming mental health content, how ad
frequency changes during late-night vulnerable-scrolling windows, and
whether ads prohibited for minors (gambling, alcohol, diet supplements)
are being served to underage users. This data has value for regulators,
researchers, journalists, and consumer protection organisations.

7\. Demo Scope & Prototype

An interactive prototype has been developed demonstrating both core
features. The prototype simulates a TikTok-style feed with realistic
content and ad distribution, and allows switching between three modes:

  --------------- --------------------------- ---------------------------
  **Mode**        **Behaviour**               **Demonstrates**

  **Shield Off**  Raw feed as the platform    The baseline: what users
                  delivers it. Ads blend      experience today with no
                  seamlessly into the content protection.
                  stream.                     

  **Ad Counter**  Floating counter tracks     Awareness mechanism: making
                  ads, revenue, and session   the invisible visible
                  time. Session report lists  shifts user behaviour.
                  all targeting brands and    
                  categories.                 

  **Scroll Mask** Pre-scrolls 8 items,        Active protection: the
                  filters ads and blocked     user's personal algorithm
                  categories, re-ranks by     overrides the platform's.
                  user interests. Scanning    
                  animation shows the mask    
                  working.                    
  --------------- --------------------------- ---------------------------

7.1 Demo Walkthrough

1.  Open the prototype and scroll through several items in "Shield Off"
    mode. Note how ads appear without friction or prominence.

2.  Switch to "Ad Counter" mode. Scroll through the same feed and
    observe the counter incrementing. Review the session report below
    the phone to see accumulated brand and category data.

3.  Switch to "Scroll Mask" mode. Tap scroll and observe the scanning
    overlay while the mask pre-processes 8 items. Note that only content
    matching user interests appears --- ads and blocked categories are
    silently removed.

4.  Compare the mask report (items scanned, blocked, and passed) with
    the ad counter report to see how much unwanted content was
    intercepted.

8\. Development Roadmap

  ------------ --------------- -------------------------------------------
  **Phase**    **Timeline**    **Deliverables**

  **Phase 1**  Months 1--3     Android accessibility-service prototype
                               with ad counter overlay for TikTok.
                               On-device ad label detection model
                               (OCR-based). User preference onboarding
                               flow.

  **Phase 2**  Months 4--6     Scroll mask implementation with real-time
                               feed interception. Content classification
                               model (ad type, category, sentiment).
                               Signature database integration from agent
                               fleet.

  **Phase 3**  Months 7--9     User preference learning from post-session
                               feedback. Multi-platform support (Instagram
                               Reels, YouTube Shorts, X). Public
                               advertising intelligence dashboard.

  **Phase 4**  Months 10--12   iOS integration pathway (partnership
                               discussions or jailbreak-free
                               implementation). Open-source release of
                               agent fleet tooling. Regulatory engagement
                               (EU DSA, national digital wellness
                               authorities).
  ------------ --------------- -------------------------------------------

9\. Estimated Budget

  ----------------------------------- ------------------------ -----------------
  **Item**                            **12-Month Cost**        **Notes**

  Core engineering team (4 engineers) €480,000--€640,000       Mobile, ML,
                                                               backend

  ML model development and training   €80,000--€120,000        Compute +
                                                               labelling

  Agent fleet infrastructure (device  €60,000--€100,000        Cloud + proxy
  farm, proxies)                                               services

  UX/UI design and user research      €60,000--€80,000         1 designer +
                                                               testing

  Legal and regulatory advisory       €30,000--€50,000         TOS, GDPR, DSA

  **Total estimated range**           **€710,000--€990,000**   **Year 1**
  ----------------------------------- ------------------------ -----------------

10\. Conclusion

The attention economy's power rests on a single asymmetry: platforms
know everything about how they manipulate your feed, and you know
nothing. ScrollShield eliminates that asymmetry.

The ad counter makes the invisible visible. The scroll mask gives the
user an algorithm of their own. The agent fleet maps the entire
advertising ecosystem in real time. Together, these components represent
a complete inversion of the power structure of the attention economy ---
transforming the smartphone from a tool of manipulation into a tool of
sovereignty.

We are not proposing to ban doomscrolling, shame users, or fight
billion-dollar companies in court. We are proposing to give every phone
owner a better agent than any platform has. Once users taste that
control, they will not want to give it back.

*End of Proposal*
