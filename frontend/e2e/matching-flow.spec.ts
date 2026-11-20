import { test, expect } from "@playwright/test";

test.describe("LifeLine End-to-End Workflow", () => {
  test("Workflow: Request → Ranked List → Decline → Next Match", async ({ page }) => {
    // 1. Mock Authentication
    await page.route("**/api/v1/auth/login", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          token: "mock-valid-jwt-token-for-staff",
          username: "staff_central",
          email: "staff1@lifeline.test",
          role: "BANK_STAFF",
        }),
      });
    });

    // Initial matches for Request #101: Donor 1 is Top Match
    let declined = false;

    await page.route("**/api/v1/requests/101", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: 101,
          bloodGroup: "O_POS",
          unitsRequested: 1,
          urgency: "CRITICAL",
          zone: "Z01",
          status: "OPEN",
          source: "STAFF",
          createdAt: new Date().toISOString(),
        }),
      });
    });

    await page.route("**/api/v1/requests/101/matches*", async (route) => {
      if (!declined) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              rankPosition: 1,
              donorId: 1,
              bloodGroup: "O_POS",
              zone: "Z01",
              totalScore: 0.912,
              plainLanguageExplanation: "Top factor: PROXIMITY (+0.80). Close to recipient hospital (0.0 km). Exact ABO/Rh match.",
              reasonCode: "PROXIMITY_DOMINANT",
              factorContributions: { PROXIMITY: 0.80, RARITY: 0.112 },
            },
            {
              rankPosition: 2,
              donorId: 2,
              bloodGroup: "O_POS",
              zone: "Z02",
              totalScore: 0.745,
              plainLanguageExplanation: "Compatible exact ABO/Rh match in neighboring zone.",
              reasonCode: "COMMON_TYPE_MATCH",
              factorContributions: { PROXIMITY: 0.60, RARITY: 0.145 },
            },
          ]),
        });
      } else {
        // After decline, Donor 1 is removed; Donor 2 becomes Top Match
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              rankPosition: 1,
              donorId: 2,
              bloodGroup: "O_POS",
              zone: "Z02",
              totalScore: 0.745,
              plainLanguageExplanation: "Top factor: PROXIMITY (+0.60). Surfaced as next-best candidate following prior donor decline.",
              reasonCode: "NEXT_BEST_SURFACED",
              factorContributions: { PROXIMITY: 0.60, RARITY: 0.145 },
            },
            {
              rankPosition: 2,
              donorId: 3,
              bloodGroup: "O_NEG",
              zone: "Z01",
              totalScore: 0.680,
              plainLanguageExplanation: "Compatible universal donor (O-). Conserved common type match.",
              reasonCode: "RARE_TYPE_CONSERVED",
              factorContributions: { PROXIMITY: 0.55, RARITY: 0.130 },
            },
          ]),
        });
      }
    });

    await page.route("**/api/v1/requests/101/notify-donor/1", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          matchLogId: 9001,
          requestId: 101,
          bloodGroup: "O_POS",
          urgency: "CRITICAL",
          outcome: "NOTIFIED",
        }),
      });
    });

    await page.route("**/api/v1/requests/match-logs/9001/record-response*", async (route) => {
      declined = true;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          matchLogId: 9001,
          requestId: 101,
          outcome: "DECLINED",
        }),
      });
    });

    // 2. Visit Login page and authenticate
    await page.goto("/login");

    // Verify Clinical Caveat is prominently displayed on the page
    const disclaimer = page.locator(".clinical-disclaimer-banner");
    await expect(disclaimer).toBeVisible();
    await expect(disclaimer).toContainText("Clinical Caveat:");
    await expect(disclaimer).toContainText("Final clinical eligibility, blood typing, cross-matching, and collection decisions belong exclusively to qualified blood-bank staff");

    // Fill in staff credentials
    await page.fill('input[placeholder="Enter username"]', "staff_central");
    await page.fill('input[placeholder="Enter password"]', "password123");
    await page.click('button[type="submit"]');

    // 3. Navigate directly to Ranked Matches for Request #101
    await page.goto("/staff/requests/101/matches");

    // Clinical disclaimer banner must also be visible here
    await expect(disclaimer).toBeVisible();

    // 4. Verify Ranked List displays Donor #1 as Top Match
    await expect(page.locator('[data-testid="match-card-1"]')).toBeVisible();
    await expect(page.locator('[data-testid="match-card-1"]')).toContainText("Top Match");
    await expect(page.locator('[data-testid="match-score-1"]')).toContainText("0.912");
    await expect(page.locator('[data-testid="explanation-1"]')).toContainText("Top factor: PROXIMITY (+0.80)");

    // Factor breakdown contributions sum to score
    await expect(page.locator('[data-testid="factor-row-proximity"]')).toContainText("+0.800");

    // 5. Staff initiates outreach notification
    await page.click('[data-testid="notify-donor-btn-1"]');
    await expect(page.locator(".alert-success")).toContainText("Outreach notification sent to Donor #1");

    // 6. Record Decline
    await page.click('[data-testid="record-decline-btn"]');

    // 7. Verify Fallback: System re-ranks and surfaces Donor #2 as the Next Match!
    await expect(page.locator(".alert-success")).toContainText("Recorded response: DECLINED");
    await expect(page.locator('[data-testid="match-card-2"]')).toBeVisible();
    await expect(page.locator('[data-testid="match-card-2"]')).toContainText("Top Match");
    await expect(page.locator('[data-testid="explanation-2"]')).toContainText("Surfaced as next-best candidate following prior donor decline");
  });
});
