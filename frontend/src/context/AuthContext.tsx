import React, { createContext, useContext, useState, useEffect } from "react";
import { api, type AuthResponse, type AuthRequest, type RegisterDonorRequest } from "../api/client";

interface AuthContextType {
  user: AuthResponse | null;
  role: "DONOR" | "BANK_STAFF" | "ADMIN" | null;
  isAuthenticated: boolean;
  login: (req: AuthRequest) => Promise<AuthResponse>;
  registerDonor: (req: RegisterDonorRequest) => Promise<AuthResponse>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthResponse | null>(() => api.getCurrentUser());

  useEffect(() => {
    const current = api.getCurrentUser();
    setUser(current);
  }, []);

  const login = async (req: AuthRequest): Promise<AuthResponse> => {
    const res = await api.login(req);
    api.setAuth(res);
    setUser(res);
    return res;
  };

  const registerDonor = async (req: RegisterDonorRequest): Promise<AuthResponse> => {
    const res = await api.registerDonor(req);
    api.setAuth(res);
    setUser(res);
    return res;
  };

  const logout = () => {
    api.clearAuth();
    setUser(null);
  };

  const role = user?.role as ("DONOR" | "BANK_STAFF" | "ADMIN") | null;

  return (
    <AuthContext.Provider
      value={{
        user,
        role: role || null,
        isAuthenticated: !!user && !!user.token,
        login,
        registerDonor,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
