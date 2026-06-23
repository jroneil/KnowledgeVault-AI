"use client";

import React, { createContext, useContext, useState, useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";

export interface UserSession {
  userId: number;
  username: string;
  email: string;
  roles: string[];
}

interface AuthContextType {
  token: string | null;
  user: UserSession | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (token: string, user: UserSession) => void;
  logout: () => void;
  hasRole: (role: string) => boolean;
  isAdmin: () => boolean;
  isContributor: () => boolean;
  apiFetch: (url: string, options?: RequestInit) => Promise<Response>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<UserSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    // Load auth from localStorage on mount
    const storedToken = localStorage.getItem("kv_token");
    const storedUser = localStorage.getItem("kv_user");

    if (storedToken && storedUser) {
      try {
        setToken(storedToken);
        setUser(JSON.parse(storedUser));
      } catch (e) {
        console.error("Failed to parse stored user", e);
        // Clear corrupt data
        localStorage.removeItem("kv_token");
        localStorage.removeItem("kv_user");
      }
    }
    setIsLoading(false);
  }, []);

  // Handle route protection
  useEffect(() => {
    if (isLoading) return;

    const isAuthPage = pathname?.startsWith("/login");
    const isAuthenticatedState = !!token;

    if (!isAuthenticatedState && !isAuthPage) {
      router.push("/login");
    } else if (isAuthenticatedState && isAuthPage) {
      router.push("/dashboard");
    }
  }, [token, pathname, isLoading, router]);

  const login = (newToken: string, newUser: UserSession) => {
    localStorage.setItem("kv_token", newToken);
    localStorage.setItem("kv_user", JSON.stringify(newUser));
    setToken(newToken);
    setUser(newUser);
    router.push("/dashboard");
  };

  const logout = () => {
    localStorage.removeItem("kv_token");
    localStorage.removeItem("kv_user");
    setToken(null);
    setUser(null);
    router.push("/login");
  };

  const hasRole = (role: string): boolean => {
    return user?.roles.includes(role) || false;
  };

  const isAdmin = (): boolean => hasRole("ADMIN");
  const isContributor = (): boolean => hasRole("CONTRIBUTOR") || hasRole("ADMIN");

  const apiFetch = async (url: string, options: RequestInit = {}): Promise<Response> => {
    const headers = new Headers(options.headers || {});
    
    // Add bearer token if available
    const activeToken = token || localStorage.getItem("kv_token");
    if (activeToken) {
      headers.set("Authorization", `Bearer ${activeToken}`);
    }

    // Default content type to json if body is present and not multipart
    if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    const targetUrl = url.startsWith("http") ? url : `${API_BASE_URL}${url}`;
    
    const response = await fetch(targetUrl, {
      ...options,
      headers,
    });

    // Handle session expiration
    if (response.status === 401) {
      logout();
    }

    return response;
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isAuthenticated: !!token,
        isLoading,
        login,
        logout,
        hasRole,
        isAdmin,
        isContributor,
        apiFetch,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
