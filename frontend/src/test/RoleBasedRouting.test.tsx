import React from "react";
import { describe, it, expect, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "../context/AuthContext";
import { ProtectedRoute } from "../components/ProtectedRoute";
import { ClinicalDisclaimerBanner } from "../components/ClinicalDisclaimerBanner";
import { api } from "../api/client";

const TestScreen: React.FC<{ name: string }> = ({ name }) => (
  <div data-testid={`screen-${name.toLowerCase().replace(/\s+/g, "-")}`}>
    {name} Screen
  </div>
);

describe("Role-Based Routing & Clinical Disclaimer", () => {
  beforeEach(() => {
    api.clearAuth();
  });

  it("renders clinical caveat disclaimer on all screens", () => {
    render(<ClinicalDisclaimerBanner />);

    const disclaimer = screen.getByRole("alert");
    expect(disclaimer).toBeInTheDocument();
    expect(disclaimer).toHaveTextContent("Clinical Caveat:");
    expect(disclaimer).toHaveTextContent("The LifeLine system routes and ranks blood donor and inventory candidates");
    expect(disclaimer).toHaveTextContent("Final clinical eligibility, blood typing, cross-matching, and collection decisions belong exclusively to qualified blood-bank staff");
  });

  it("redirects unauthenticated users to /login when accessing protected routes", () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/staff/review"]}>
          <Routes>
            <Route path="/login" element={<TestScreen name="Login" />} />
            <Route
              path="/staff/review"
              element={
                <ProtectedRoute allowedRoles={["BANK_STAFF", "ADMIN"]}>
                  <TestScreen name="Staff Review" />
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByTestId("screen-login")).toBeInTheDocument();
  });

  it("permits BANK_STAFF to access staff review queue", () => {
    api.setAuth({
      token: "mock-staff-jwt",
      username: "staff_central",
      role: "BANK_STAFF",
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/staff/review"]}>
          <Routes>
            <Route path="/login" element={<TestScreen name="Login" />} />
            <Route
              path="/staff/review"
              element={
                <ProtectedRoute allowedRoles={["BANK_STAFF", "ADMIN"]}>
                  <TestScreen name="Staff Review" />
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByTestId("screen-staff-review")).toBeInTheDocument();
  });

  it("redirects DONOR role attempting to access staff route to /donor", () => {
    api.setAuth({
      token: "mock-donor-jwt",
      username: "donor_arjun",
      role: "DONOR",
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/staff/review"]}>
          <Routes>
            <Route path="/donor" element={<TestScreen name="Donor Portal" />} />
            <Route
              path="/staff/review"
              element={
                <ProtectedRoute allowedRoles={["BANK_STAFF", "ADMIN"]}>
                  <TestScreen name="Staff Review" />
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByTestId("screen-donor-portal")).toBeInTheDocument();
  });

  it("permits ADMIN role to access admin console", () => {
    api.setAuth({
      token: "mock-admin-jwt",
      username: "admin",
      role: "ADMIN",
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/admin"]}>
          <Routes>
            <Route path="/login" element={<TestScreen name="Login" />} />
            <Route
              path="/admin"
              element={
                <ProtectedRoute allowedRoles={["ADMIN"]}>
                  <TestScreen name="Admin Console" />
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByTestId("screen-admin-console")).toBeInTheDocument();
  });
});
