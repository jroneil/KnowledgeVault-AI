'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../context/AuthContext'

interface Collection {
  id: number
  name: string
  description: string | null
  isActive: boolean
  createdBy: number
  createdAt: string
  updatedAt: string
}

export default function CollectionsPage() {
  const [collections, setCollections] = useState<Collection[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const { apiFetch, token } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!token) {
      router.push('/login')
      return
    }
    let cancelled = false
    apiFetch('/api/v1/collections')
      .then(async response => {
        if (!response.ok) throw new Error('Failed to fetch collections')
        return response.json()
      })
      .then(data => {
        if (!cancelled) setCollections(data.collections ?? data)
      })
      .catch(err => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'An error occurred')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [token, router, apiFetch])

  const handleCreateCollection = () => {
    router.push('/collections/new')
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading collections...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-red-600">Error: {error}</div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Collections</h1>
          <p className="text-gray-600 mt-2">Manage your document collections</p>
        </div>
        <button
          onClick={handleCreateCollection}
          className="bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors"
        >
          Create Collection
        </button>
      </div>

      {collections.length === 0 ? (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg mb-4">No collections found</div>
          <p className="text-gray-400 mb-6">Create your first collection to start organizing documents</p>
          <button
            onClick={handleCreateCollection}
            className="bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors"
          >
            Create Collection
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {collections.map((collection) => (
            <div
              key={collection.id}
              className={`bg-white rounded-lg shadow-md p-6 border-2 ${
                collection.isActive ? 'border-green-200' : 'border-gray-200 opacity-60'
              }`}
            >
              <div className="flex justify-between items-start mb-4">
                <h2 className="text-xl font-semibold text-gray-900">{collection.name}</h2>
                <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                  collection.isActive
                    ? 'bg-green-100 text-green-800'
                    : 'bg-gray-100 text-gray-800'
                }`}>
                  {collection.isActive ? 'Active' : 'Inactive'}
                </span>
              </div>
              
              {collection.description && (
                <p className="text-gray-600 mb-4 line-clamp-2">{collection.description}</p>
              )}
              
              <div className="flex justify-between items-center text-sm text-gray-500 mb-4">
                <span>Created: {new Date(collection.createdAt).toLocaleDateString()}</span>
              </div>
              
              <Link
                href={`/documents?collectionId=${collection.id}`}
                className="block text-center bg-blue-50 text-blue-600 px-4 py-2 rounded-lg hover:bg-blue-100 transition-colors"
              >
                View Documents
              </Link>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
