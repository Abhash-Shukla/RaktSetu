# LifeLine / RaktSetu Makefile for Phase 6 Artifacts Reproduction

.PHONY: all results clean verify

all: results

results:
	python scripts/make_results.py

verify:
	python scripts/verify_historical.py
