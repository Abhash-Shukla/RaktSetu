import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { ClinicalDisclaimerBanner } from "./components/ClinicalDisclaimerBanner";
import { Header } from "./components/Header";
import { ProtectedRoute } from "./components/ProtectedRoute";

import { LoginPage } from "./pages/LoginPage";
import { PublicIntakePage } from "./pages/PublicIntakePage";
import { StaffReviewQueuePage } from "./pages/StaffReviewQueuePage";
import { StaffRequestsPage } from "./pages/StaffRequestsPage";
import { StaffRankedMatchesPage } from "./pages/StaffRankedMatchesPage";
import { DonorPortalPage } from "./pages/DonorPortalPage";
import { AdminConfigPage } from "./pages/AdminConfigPage";

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <div className="app-container">
          {/* Clinical caveat displayed prominently across every screen */}
          <ClinicalDisclaimerBanner />

          {/* Navigation header with role-based routing */}
          <Header />

          {/* Main page content container */}
          <main className="main-content">
            <Routes>
              {/* Public Intake (unauthenticated) */}
              <Route path="/" element={<PublicIntakePage />} />

              {/* Login & Registration */}
              <Route path="/login" element={<LoginPage />} />

              {/* Staff Routes */}
              <Route
                path="/staff/review"
                element={
                  <ProtectedRoute allowedRoles={["BANK_STAFF", "ADMIN"]}>
                    <StaffReviewQueuePage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/staff/requests"
                element={
                  <ProtectedRoute allowedRoles={["BANK_STAFF", "ADMIN"]}>
                    <StaffRequestsPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/staff/requests/:id/matches"
                element={
                  <ProtectedRoute allowedRoles={["BANK_STAFF", "ADMIN"]}>
                    <StaffRankedMatchesPage />
                  </ProtectedRoute>
                }
              />

              {/* Donor Routes */}
              <Route
                path="/donor"
                element={
                  <ProtectedRoute allowedRoles={["DONOR"]}>
                    <DonorPortalPage />
                  </ProtectedRoute>
                }
              />

              {/* Admin Routes */}
              <Route
                path="/admin"
                element={
                  <ProtectedRoute allowedRoles={["ADMIN"]}>
                    <AdminConfigPage />
                  </ProtectedRoute>
                }
              />

              {/* Fallback */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
