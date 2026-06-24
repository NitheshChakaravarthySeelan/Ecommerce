"use client";

import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from "react";
import { api } from "./api";

interface User {
  id: string;
  email: string;
  role: string;
}

interface AuthContextType {
  user: User | null;
  token: string;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => void;
  getAuthHeaders: () => Record<string, string>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  token: "",
  login: async () => {},
  register: async () => {},
  logout: () => {},
  getAuthHeaders: (): Record<string, string> => ({}),
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState("");
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const saved = localStorage.getItem("jwt_token");
    if (saved) {
      setToken(saved);
      fetchUser(saved);
    }
  }, []);

  async function fetchUser(jwt: string) {
    try {
      const res = await fetch(`${api.auth}/me`, {
        headers: { Authorization: `Bearer ${jwt}` },
      });
      if (res.ok) setUser(await res.json());
    } catch { /* ignore */ }
  }

  const getAuthHeaders = useCallback((): Record<string, string> => {
    if (!token) return {};
    return { Authorization: `Bearer ${token}` };
  }, [token]);

  async function login(email: string, password: string) {
    const res = await fetch(`${api.auth}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!res.ok) throw new Error("Login failed");
    const data = await res.json();
    localStorage.setItem("jwt_token", data.accessToken);
    setToken(data.accessToken);
    await fetchUser(data.accessToken);
  }

  async function register(email: string, password: string) {
    const res = await fetch(`${api.auth}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!res.ok) throw new Error("Registration failed");
    const data = await res.json();
    localStorage.setItem("jwt_token", data.accessToken);
    setToken(data.accessToken);
    await fetchUser(data.accessToken);
  }

  function logout() {
    localStorage.removeItem("jwt_token");
    setToken("");
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout, getAuthHeaders }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
