# ADR 002: Rate Limiting Algorithm Selection

## Status
Accepted

## Context
TO build an API gateway

## Options Considered
Token Bucket vs Sliding Window

## Decision
Token Bucket

## Reasoning
Token Bucket algorithm is perfect case of api-gateway as it allows bursts for the client provided the client has enough tokens in the bucket.Ideally there is no guarantee
that each client will make same number of API calls in any given time slot so Sliding Window doesn't work here in gateway design.

## Consequences
We need to track the two things for any given client. i.e Number of tokens available and rate at which tokens need to fill 
