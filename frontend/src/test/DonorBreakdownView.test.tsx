import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DonorBreakdownView } from "../components/DonorBreakdownView";
import type { RankedMatchDto } from "../api/client";

describe("DonorBreakdownView Component", () => {
  const mockMatches: RankedMatchDto[] = [
    {
      rankPosition: 1,
      donorId: 101,
      bloodGroup: "O_POS",
      zone: "Z01",
      totalScore: 0.875,
      plainLanguageExplanation: "Top factor: PROXIMITY (contribution: +0.70). Close to recipient hospital (0.0 km). Exact ABO/Rh match.",
      reasonCode: "PROXIMITY_DOMINANT",
      factorContributions: {
        PROXIMITY: 0.70,
        RARITY: 0.175,
      },
    },
    {
      rankPosition: 2,
      donorId: 102,
      bloodGroup: "O_NEG",
      zone: "Z02",
      totalScore: 0.720,
      plainLanguageExplanation: "Rare type (O-): reserved unless no common-type match is available. STALE_LOCATION confirmed.",
      reasonCode: "RARE_TYPE_CONSERVED",
      factorContributions: {
        PROXIMITY: 0.52,
        RARITY: 0.20,
      },
    },
  ];

  it("renders ranked matches with scores and plain-language explanations", () => {
    render(<DonorBreakdownView matches={mockMatches} />);

    expect(screen.getByTestId("donor-matches-list")).toBeInTheDocument();
    expect(screen.getByText("Donor #101")).toBeInTheDocument();
    expect(screen.getByText("Donor #102")).toBeInTheDocument();

    expect(screen.getByTestId("explanation-101")).toHaveTextContent("Top factor: PROXIMITY");
    expect(screen.getByTestId("match-score-101")).toHaveTextContent("0.875");
  });

  it("expands per-factor breakdown showing individual contributions", () => {
    render(<DonorBreakdownView matches={mockMatches} />);

    // First match is expanded by default
    expect(screen.getByTestId("factor-breakdown-101")).toBeInTheDocument();
    expect(screen.getByTestId("factor-row-proximity")).toHaveTextContent("+0.700");
    expect(screen.getByTestId("factor-row-rarity")).toHaveTextContent("+0.175");

    // Toggle expand for match 2
    const toggleBtn2 = screen.getByTestId("toggle-expand-102");
    fireEvent.click(toggleBtn2);
    expect(screen.getByTestId("factor-breakdown-102")).toBeInTheDocument();
  });

  it("displays STALE_LOCATION and rare-type conservation flags", () => {
    render(<DonorBreakdownView matches={mockMatches} />);

    expect(screen.getByTestId("stale-location-badge")).toBeInTheDocument();
    expect(screen.getByTestId("stale-location-badge")).toHaveTextContent("STALE_LOCATION");
    expect(screen.getByTestId("rare-type-badge")).toBeInTheDocument();
    expect(screen.getByTestId("rare-type-badge")).toHaveTextContent("RARE_TYPE_CONSERVED");
  });

  it("calls onNotifyDonor when outreach button is clicked", () => {
    const handleNotify = vi.fn();
    render(<DonorBreakdownView matches={mockMatches} onNotifyDonor={handleNotify} />);

    const notifyBtn = screen.getByTestId("notify-donor-btn-101");
    fireEvent.click(notifyBtn);

    expect(handleNotify).toHaveBeenCalledWith(101);
  });

  it("calls onRecordResponse with DECLINED when decline button is clicked", () => {
    const handleRecord = vi.fn();
    render(
      <DonorBreakdownView
        matches={mockMatches}
        activeMatchLogId={501}
        onRecordResponse={handleRecord}
      />
    );

    const declineBtn = screen.getByTestId("record-decline-btn");
    fireEvent.click(declineBtn);

    expect(handleRecord).toHaveBeenCalledWith(501, "DECLINED");
  });
});
