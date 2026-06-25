"use client";

import React, { createContext, useCallback, useContext, useState, useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { getApiUrl } from "../../lib/api";

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

interface StoredSession {
  token: string | null;
  user: UserSession | null;
}

function readStoredSession(): StoredSession {
  if (typeof window === "undefined") {
    return { token: null, user: null };
  }

  const storedToken = localStorage.getItem("kv_token");
  const storedUser = localStorage.getItem("kv_user");
  if (!storedToken || !storedUser) {
    return { token: null, user: null };
  }

  try {
    return { token: storedToken, user: JSON.parse(storedUser) as UserSession };
  } catch {
    localStorage.removeItem("kv_token");
    localStorage.removeItem("kv_user");
    return { token: null, user: null };
  }
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSession] = useState<StoredSession>({ token: null, user: null });
  const [isLoading, setIsLoading] = useState(true);
  const { token, user } = session;
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setSession(readStoredSession());
      setIsLoading(false);
    }, 0);
    return () => window.clearTimeout(timeoutId);
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

  const login = useCallback((newToken: string, newUser: UserSession) => {
    localStorage.setItem("kv_token", newToken);
    localStorage.setItem("kv_user", JSON.stringify(newUser));
    setSession({ token: newToken, user: newUser });
    router.push("/dashboard");
  }, [router]);

  const logout = useCallback(() => {
    localStorage.removeItem("kv_token");
    localStorage.removeItem("kv_user");
    setSession({ token: null, user: null });
    router.push("/login");
  }, [router]);

  const hasRole = (role: string): boolean => {
    return user?.roles.includes(role) || false;
  };

  const isAdmin = (): boolean => hasRole("ADMIN");
  const isContributor = (): boolean => hasRole("CONTRIBUTOR") || hasRole("ADMIN");

  const apiFetch = useCallback(async (url: string, options: RequestInit = {}): Promise<Response> => {
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

    const targetUrl = url.startsWith("http") ? url : `${getApiUrl()}${url}`;
    
    const response = await fetch(targetUrl, {
      ...options,
      headers,
    });

    // Handle session expiration
    if (response.status === 401) {
      logout();
    }

    return response;
  }, [token, logout]);

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
