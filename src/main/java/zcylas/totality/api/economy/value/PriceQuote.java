package zcylas.totality.api.economy.value;

/**
 * A server-computed price quote (design document Section 1b/1e). The server always computes
 * this; the client only ever displays a quote it was sent, and the server re-validates at
 * commit time rather than trusting a stale client-held quote. No networking/GUI wiring exists
 * yet in this phase — this record only carries enough information for that later work.
 */
public record PriceQuote(long unitPrice, int quantity, long total, PricingDirection direction) {}
