"use client";

import React, { useState } from "react";
import { useAuth } from "../context/AuthContext";
import Link from "next/link";
import { usePathname } from "next/navigation";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, logout, isLoading, isAuthenticated } = useAuth();
  const pathname = usePathname();
  const [sidebarOpen, setSidebarOpen] = useState(true);

  if (isLoading) {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-zinc-950 text-white">
        <div className="flex flex-col items-center gap-4">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-zinc-700 border-t-indigo-500" />
          <p className="text-sm font-medium text-zinc-400">Loading your vault...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null; // Let AuthContext redirect to /login
  }

  const navItems = [
    {
      name: "Dashboard",
      href: "/dashboard",
      icon: (
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2H6a2 2 0 01-2-2v-4zM14 16a2 2 0 012-2h2a2 2 0 012 2v4a2 2 0 01-2 2h-2a2 2 0 01-2-2v-4z" />
        </svg>
      ),
    },
    {
      name: "Collections",
      href: "/dashboard/collections",
      icon: (
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
        </svg>
      ),
    },
    {
      name: "Documents",
      href: "/dashboard/documents",
      icon: (
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      ),
    },
    {
      name: "Search Panel",
      href: "/dashboard/search",
      icon: (
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
      ),
    },
  ];

  // VIEWER role shouldn't see Upload tab (visual hide)
  const isContributor = user?.roles.includes("ADMIN") || user?.roles.includes("CONTRIBUTOR");
  if (isContributor) {
    navItems.push({
      name: "Upload Document",
      href: "/dashboard/documents/upload",
      icon: (
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
        </svg>
      ),
    });
  }

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-zinc-950 text-zinc-100 font-sans antialiased">
      {/* Sidebar navigation */}
      <aside
        className={`relative z-20 flex flex-col border-r border-zinc-900 bg-zinc-900/20 backdrop-blur-xl transition-all duration-300 ${
          sidebarOpen ? "w-64" : "w-20"
        }`}
      >
        {/* Brand Header */}
        <div className="flex h-16 items-center justify-between px-6 border-b border-zinc-900">
          <Link href="/dashboard" className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-tr from-indigo-500 to-violet-600 shadow-md">
              <svg className="h-5 w-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
            {sidebarOpen && (
              <span className="text-lg font-bold tracking-tight text-white">
                Knowledge<span className="text-indigo-400">Vault</span>
              </span>
            )}
          </Link>
        </div>

        {/* Navigation Items */}
        <nav className="flex-1 space-y-1.5 px-4 py-6">
          {navItems.map((item) => {
            const isActive = pathname === item.href || pathname?.startsWith(item.href + "/");
            return (
              <Link
                key={item.name}
                href={item.href}
                className={`group flex items-center gap-4 rounded-xl px-4 py-3 text-sm font-semibold transition-all duration-200 ${
                  isActive
                    ? "bg-gradient-to-r from-indigo-500/10 to-violet-600/10 text-indigo-400 border-l-2 border-indigo-500"
                    : "text-zinc-400 hover:bg-zinc-900/60 hover:text-white"
                }`}
              >
                <div className={`shrink-0 transition-colors ${isActive ? "text-indigo-400" : "text-zinc-400 group-hover:text-white"}`}>
                  {item.icon}
                </div>
                {sidebarOpen && <span>{item.name}</span>}
              </Link>
            );
          })}
        </nav>

        {/* Profile Card & Logout */}
        <div className="p-4 border-t border-zinc-900">
          <div className="flex items-center justify-between gap-3 rounded-2xl bg-zinc-900/40 p-3">
            {sidebarOpen && (
              <div className="min-w-0">
                <p className="truncate text-sm font-bold text-white">{user?.username}</p>
                <p className="truncate text-xs text-zinc-500">{user?.email}</p>
                <span className="mt-1 inline-block rounded-md bg-indigo-500/10 px-1.5 py-0.5 text-[10px] font-bold text-indigo-400">
                  {user?.roles[0]}
                </span>
              </div>
            )}
            <button
              onClick={logout}
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-zinc-950 text-zinc-400 hover:bg-red-500/10 hover:text-red-400 transition-colors"
              title="Sign Out"
            >
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top Header Bar */}
        <header className="flex h-16 items-center justify-between px-8 border-b border-zinc-900 bg-zinc-950/20 backdrop-blur-xl">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="flex h-9 w-9 items-center justify-center rounded-xl border border-zinc-900 bg-zinc-900/20 hover:bg-zinc-900/60 transition-colors"
          >
            <svg className="h-5 w-5 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <div className="flex items-center gap-4">
            <span className="text-xs text-zinc-500">Service Status:</span>
            <div className="flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-semibold text-emerald-400">
              <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              Connected
            </div>
          </div>
        </header>

        {/* Scrollable Viewport */}
        <main className="flex-1 overflow-y-auto bg-zinc-950 p-8">
          {children}
        </main>
      </div>
    </div>
  );
}
