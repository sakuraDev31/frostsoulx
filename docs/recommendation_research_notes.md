# Contextual music recommendation research

The implementation uses a hybrid, context-aware approach rather than relying on a single collaborative-filter model.

The review **Content filtering methods for music recommendation** explains that collaborative filtering uses similar listening patterns but suffers from sparse interactions and popularity bias; content-aware signals help mitigate those limitations. It also describes mood/context features such as tempo, mode, timbre, valence, and arousal as useful for context-sensitive recommendation.

Source: https://arxiv.org/html/2507.02282v1

The overview **Diversity by Design in Music Recommender Systems** recommends treating diversity as a design objective, not only optimizing prediction accuracy. The implementation therefore caps repeated artists and mixes familiar history-derived tracks with related/new candidates.

Source: https://transactions.ismir.net/articles/10.5334/tismir.106

The survey **A Survey of Music Recommendation Systems** describes implicit feedback such as play counts and skips, item/user similarity, sequential listening, repeat listening, and mood/context as core signals for music recommendation. The app already has recommendation signal types including complete, replay, skip, unlike, favorite, and dislike; those are retained as ranking signals.

Source: https://dl.acm.org/doi/fullHtml/10.1145/3671151.3671243

## Applied algorithm

For the Home mode-specific page, use this order:

1. Current mode context and selected Home chip.
2. Recent listening/history and completion/replay/favorite signals.
3. Similar artist/song candidates already fetched by the app.
4. Negative feedback exclusion, especially dislike.
5. Familiarity versus discovery balance.
6. Artist diversification, with at most two tracks from one artist in the visible set.
7. Local/offline candidates as a fallback when remote candidates are unavailable.

The empty-page fix should not invent data. It should fall back through existing app-owned candidates: `forThisMoment`, `featuredForYou`, `recentlyPlayed`, `quickPicks`, and `keepListening`.

## Offline engine ranking signals

`OfflineRecommendationEngine.rank()` now also uses:

- **Sequential/co-occurrence affinity** (`buildSequenceAffinity`): a personal, session-derived
  "usually played after the last few tracks" signal, built purely from this listener's own signal
  history (no server or other-user data). This was the one signal type named by the cited survey
  (sequential listening) that the ranking formula did not previously consume; it now feeds both the
  score and a dedicated `Continue Listening` shelf.
- **Listen-fraction-aware Skip weighting**: a `Skip` signal's positive/negative contribution now
  scales with how much of the track had already played (`listenedMs / duration`) instead of being a
  flat constant, so skipping near the end of a track is no longer scored the same as skipping in the
  first few seconds.
