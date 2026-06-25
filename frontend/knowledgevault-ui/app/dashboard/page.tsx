'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { FileText, Folder, Search, Users, TrendingUp, Clock, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function DashboardPage() {
  const router = useRouter();
  const { isLoading: authLoading, logout, token } = useAuth();
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    totalDocuments: 0,
    totalCollections: 0,
    recentUploads: 0,
    totalUsers: 0
  });

  useEffect(() => {
    if (authLoading) {
      return;
    }
    if (!token) {
      router.push('/login');
      return;
    }

    // Simulate loading dashboard data
    setTimeout(() => {
      setStats({
        totalDocuments: 156,
        totalCollections: 12,
        recentUploads: 23,
        totalUsers: 8
      });
      setLoading(false);
    }, 1000);
  }, [authLoading, router, token]);

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
      title: 'Search Documents',
      description: 'Find documents by title, content, or metadata',
      icon: Search,
      href: '/search',
      color: 'bg-purple-600'
    }
  ];

  const statCards = [
    {
      title: 'Total Documents',
      value: stats.totalDocuments,
      icon: FileText,
      color: 'bg-blue-600',
      trend: '+12%'
    },
    {
      title: 'Collections',
      value: stats.totalCollections,
      icon: Folder,
      color: 'bg-green-600',
      trend: '+3%'
    },
    {
      title: 'Recent Uploads',
      value: stats.recentUploads,
      icon: Clock,
      color: 'bg-purple-600',
      trend: '+5%'
    },
    {
      title: 'Active Users',
      value: stats.totalUsers,
      icon: Users,
      color: 'bg-orange-600',
      trend: '+8%'
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
              <span className="text-green-400 text-sm">Operational</span>
            </div>
            <div className="flex items-center justify-between p-3 bg-zinc-950/50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                <span className="text-white">Database Connection</span>
              </div>
              <span className="text-green-400 text-sm">Connected</span>
            </div>
            <div className="flex items-center justify-between p-3 bg-zinc-950/50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="w-2 h-2 bg-green-500 rounded-full"></div>
                <span className="text-white">Storage Service</span>
              </div>
              <span className="text-green-400 text-sm">Available</span>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
