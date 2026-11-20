import React from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: Array<"DONOR" | "BANK_STAFF" | "ADMIN">;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
  const { isAuthenticated, role } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && role && !allowedRoles.includes(role)) {
    // If authenticated but unauthorized for this specific role, redirect to their home screen
    if (role === "DONOR") {
      return <Navigate to="/donor" replace />;
    } else if (role === "BANK_STAFF") {
      return <Navigate to="/staff/review" replace />;
    } else if (role === "ADMIN") {
      return <Navigate to="/admin" replace />;
    }
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
};
