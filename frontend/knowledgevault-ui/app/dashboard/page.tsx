'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { FileText, Folder, Search, Users, TrendingUp, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import {
  getCollectionOverviewStats,
  getDocumentOverviewStats,
  getHealth,
  getSearchStats,
  listUsers,
} from '../../lib/api';

export default function DashboardPage() {
  const router = useRouter();
  const { isLoading: authLoading, logout, token, apiFetch, isAdmin } = useAuth();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [stats, setStats] = useState({
    totalDocuments: 0,
    totalCollections: 0,
    totalEmbeddings: 0,
    totalUsers: null as number | null
  });
  const [serviceStatus, setServiceStatus] = useState({
    backend: 'Checking',
    search: 'Checking',
    auth: 'Authenticated'
  });

  useEffect(() => {
    if (authLoading) {
      return;
    }
    if (!token) {
      router.push('/login');
      return;
    }

    let cancelled = false;

    Promise.allSettled([
      getDocumentOverviewStats(apiFetch),
      getCollectionOverviewStats(apiFetch),
      getSearchStats(apiFetch),
      getHealth(),
      isAdmin() ? listUsers(apiFetch) : Promise.resolve(null),
    ])
      .then(([documentStats, collectionStats, searchStats, health, users]) => {
        if (cancelled) return;

        setStats({
          totalDocuments: documentStats.status === 'fulfilled' ? documentStats.value.totalDocuments : 0,
          totalCollections: collectionStats.status === 'fulfilled' ? collectionStats.value.totalCollections : 0,
          totalEmbeddings: searchStats.status === 'fulfilled' ? searchStats.value.totalEmbeddings : 0,
          totalUsers: users.status === 'fulfilled' && users.value ? users.value.length : null,
        });

        setServiceStatus({
          backend: health.status === 'fulfilled' && health.value.status === 'UP' ? 'Operational' : 'Unavailable',
          search: searchStats.status === 'fulfilled' ? 'Indexed' : 'Unavailable',
          auth: token ? 'Authenticated' : 'Missing session',
        });

        const failures = [documentStats, collectionStats, searchStats]
          .filter(result => result.status === 'rejected')
          .length;
        if (failures > 0) {
          setError('Some dashboard data could not be loaded.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [authLoading, router, token, apiFetch, isAdmin]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
      </div>
    );
  }

  const quickActions = [
    {
      title: 'Upload Document',
      description: 'Add a new document to your collection',
      icon: FileText,
      href: '/documents/upload',
      color: 'bg-indigo-600'
    },
    {
      title: 'Create Collection',
      description: 'Organize documents in collections',
      icon: Folder,
      href: '/collections',
      color: 'bg-green-600'
    },
    {
      title: 'Ask Documents',
      description: 'Get a grounded single-shot answer with citations',
      icon: Search,
      href: '/ask',
      color: 'bg-purple-600'
    }
  ];

  const statCards = [
    {
      title: 'Total Documents',
      value: stats.totalDocuments,
      icon: FileText,
      color: 'bg-blue-600',
      trend: 'Live'
    },
    {
      title: 'Collections',
      value: stats.totalCollections,
      icon: Folder,
      color: 'bg-green-600',
      trend: 'Live'
    },
    {
      title: 'Indexed Embeddings',
      value: stats.totalEmbeddings,
      icon: Search,
      color: 'bg-purple-600',
      trend: 'Live'
    },
    {
      title: 'Users',
      value: stats.totalUsers ?? '—',
      icon: Users,
      color: 'bg-orange-600',
      trend: isAdmin() ? 'Live' : 'Admin only'
    }
  ];

  return (
    <div className="min-h-screen bg-zinc-950">
      {/* Header */}
      <header className="bg-zinc-900/80 border-b border-zinc-800 backdrop-blur-sm sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-indigo-600 rounded-lg flex items-center justify-center">
                <FileText className="w-6 h-6 text-white" />
              </div>
              <h1 className="text-xl font-bold text-white">KnowledgeVault AI</h1>
            </div>
            <nav className="flex items-center gap-4">
              <Link
                href="/documents"
                className="text-zinc-300 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
              >
                Documents
              </Link>
              <Link
                href="/collections"
                className="text-zinc-300 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
              >
                Collections
              </Link>
              <Link
                href="/search"
                className="text-zinc-300 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
              >
                Search
              </Link>
              <Link
                href="/ask"
                className="text-zinc-300 hover:text-white px-3 py-2 rounded-md text-sm font-medium"
              >
                Ask
              </Link>
              <button
                onClick={logout}
                className="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 transition-colors"
              >
                Logout
              </button>
            </nav>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Welcome Section */}
        <div className="mb-8">
          <h2 className="text-3xl font-bold text-white mb-2">Welcome to KnowledgeVault AI</h2>
          <p className="text-zinc-400">Manage your documents and collaborate with your team</p>
        </div>

        {error && (
          <div className="mb-6 rounded-xl border border-yellow-700 bg-yellow-950/40 px-4 py-3 text-sm text-yellow-200">
            {error}
          </div>
        )}

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          {statCards.map((stat, index) => (
            <div
              key={index}
              className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 hover:border-zinc-700 transition-colors"
            >
              <div className="flex items-center justify-between mb-4">
                <div className={`w-12 h-12 ${stat.color} rounded-lg flex items-center justify-center`}>
                  <stat.icon className="w-6 h-6 text-white" />
                </div>
                <span className="text-green-400 text-sm font-medium flex items-center gap-1">
                  <TrendingUp className="w-4 h-4" />
                  {stat.trend}
                </span>
              </div>
              <h3 className="text-zinc-400 text-sm font-medium mb-1">{stat.title}</h3>
              <p className="text-2xl font-bold text-white">{stat.value}</p>
            </div>
          ))}
        </div>

        {/* Quick Actions */}
        <div className="mb-8">
          <h3 className="text-xl font-bold text-white mb-4">Quick Actions</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {quickActions.map((action, index) => (
              <Link
                key={index}
                href={action.href}
                className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 hover:border-zinc-700 transition-all hover:scale-105 group"
              >
                <div className={`w-12 h-12 ${action.color} rounded-lg flex items-center justify-center mb-4 group-hover:opacity-90 transition-opacity`}>
                  <action.icon className="w-6 h-6 text-white" />
                </div>
                <h4 className="text-lg font-semibold text-white mb-2">{action.title}</h4>
                <p className="text-zinc-400 text-sm">{action.description}</p>
              </Link>
            ))}
          </div>
        </div>

        {/* Recent Activity */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6">
          <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
            <AlertCircle className="w-5 h-5" />
            System Status
          </h3>
          <div className="space-y-4">
            <div className="flex items-center justify-between p-3 bg-zinc-950/50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                <span className="text-white">Backend Service</span>
              </div>
              <span className={`text-sm ${serviceStatus.backend === 'Operational' ? 'text-green-400' : 'text-red-400'}`}>
                {serviceStatus.backend}
              </span>
            </div>
            <div className="flex items-center justify-between p-3 bg-zinc-950/50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className={`w-2 h-2 rounded-full ${serviceStatus.search === 'Indexed' ? 'bg-green-500' : 'bg-red-500'}`}></div>
                <span className="text-white">Semantic Search Index</span>
              </div>
              <span className={`text-sm ${serviceStatus.search === 'Indexed' ? 'text-green-400' : 'text-red-400'}`}>
                {serviceStatus.search}
              </span>
            </div>
            <div className="flex items-center justify-between p-3 bg-zinc-950/50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className={`w-2 h-2 rounded-full ${serviceStatus.auth === 'Authenticated' ? 'bg-green-500' : 'bg-red-500'}`}></div>
                <span className="text-white">Authenticated Session</span>
              </div>
              <span className={`text-sm ${serviceStatus.auth === 'Authenticated' ? 'text-green-400' : 'text-red-400'}`}>
                {serviceStatus.auth}
              </span>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
