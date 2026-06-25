'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../context/AuthContext'

interface SearchResult {
  documentId: number
  collectionId: number
  collectionName: string
  title: string
  description: string
  status: string
  currentVersion: number
  product?: string
  revision?: string
  department?: string
  manufacturer?: string
  category?: string
  tags?: string[]
  effectiveDate?: string
  createdAt: string
  updatedAt: string
}

interface Collection {
  id: number
  name: string
  isActive: boolean
}

export default function SearchPage() {
  const [results, setResults] = useState<SearchResult[]>([])
  const [collections, setCollections] = useState<Collection[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { apiFetch, token } = useAuth()
  const router = useRouter()

  // Search filters
  const [title, setTitle] = useState('')
  const [collection, setCollection] = useState('')
  const [product, setProduct] = useState('')
  const [revision, setRevision] = useState('')
  const [department, setDepartment] = useState('')
  const [manufacturer, setManufacturer] = useState('')
  const [category, setCategory] = useState('')
  const [tags, setTags] = useState('')
  const [status, setStatus] = useState('')

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
      .then((data: Collection[] | { collections: Collection[] }) => {
        const collections = Array.isArray(data) ? data : data.collections
        if (!cancelled) setCollections(collections.filter(item => item.isActive))
      })
      .catch(err => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to fetch collections')
      })
    return () => {
      cancelled = true
    }
  }, [token, router, apiFetch])

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      const params = new URLSearchParams()
      if (title) params.append('title', title)
      if (collection) params.append('collection', collection)
      if (product) params.append('product', product)
      if (revision) params.append('revision', revision)
      if (department) params.append('department', department)
      if (manufacturer) params.append('manufacturer', manufacturer)
      if (category) params.append('category', category)
      if (tags) {
        const tagList = tags.split(',').map(t => t.trim()).filter(t => t)
        tagList.forEach(tag => params.append('tags', tag))
      }
      if (status) params.append('status', status)

      const response = await apiFetch(`/api/v1/search?${params.toString()}`)

      if (!response.ok) {
        throw new Error('Search failed')
      }

      const data = await response.json()
      setResults(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Search failed')
    } finally {
      setLoading(false)
    }
  }

  const handleClear = () => {
    setTitle('')
    setCollection('')
    setProduct('')
    setRevision('')
    setDepartment('')
    setManufacturer('')
    setCategory('')
    setTags('')
    setStatus('')
    setResults([])
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return 'bg-green-100 text-green-800'
      case 'ARCHIVED':
        return 'bg-yellow-100 text-yellow-800'
      case 'DELETED':
        return 'bg-red-100 text-red-800'
      default:
        return 'bg-gray-100 text-gray-800'
    }
  }

  const hasActiveFilters = () => {
    return title || collection || product || revision || department || manufacturer || category || tags || status
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-6">
        <Link href="/documents" className="text-blue-600 hover:text-blue-800">
          ← Back to Documents
        </Link>
      </div>

      <h1 className="text-3xl font-bold text-gray-900 mb-2">Advanced Search</h1>
      <p className="text-gray-600 mb-8">Search documents by multiple metadata fields</p>

      {/* Search Form */}
      <div className="bg-white shadow-md rounded-lg p-6 mb-6">
        <form onSubmit={handleSearch}>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* Title */}
            <div className="lg:col-span-2">
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Document Title
              </label>
              <input
                type="text"
                placeholder="Search by title..."
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Collection */}
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Collection
              </label>
              <select
                value={collection}
                onChange={(e) => setCollection(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">All Collections</option>
                {collections.map(c => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Product */}
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Product
              </label>
              <input
                type="text"
                placeholder="Product name"
                value={product}
                onChange={(e) => setProduct(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Revision */}
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Revision
              </label>
              <input
                type="text"
                placeholder="Revision number"
                value={revision}
                onChange={(e) => setRevision(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Department */}
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Department
              </label>
              <input
                type="text"
                placeholder="Department name"
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Manufacturer */}
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Manufacturer
              </label>
              <input
                type="text"
                placeholder="Manufacturer name"
                value={manufacturer}
                onChange={(e) => setManufacturer(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Category */}
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Category
              </label>
              <input
                type="text"
                placeholder="Document category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {/* Status */}
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Status
              </label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">All Status</option>
                <option value="ACTIVE">Active</option>
                <option value="ARCHIVED">Archived</option>
                <option value="DELETED">Deleted</option>
              </select>
            </div>

            {/* Tags */}
            <div className="lg:col-span-3">
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Tags
              </label>
              <input
                type="text"
                placeholder="Comma-separated tags (e.g., important, review, q1)"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                Separate multiple tags with commas
              </p>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="mt-6 flex space-x-4">
            <button
              type="submit"
              disabled={loading || !hasActiveFilters()}
              className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
            >
              {loading ? 'Searching...' : 'Search'}
            </button>
            <button
              type="button"
              onClick={handleClear}
              className="px-6 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50"
            >
              Clear Filters
            </button>
          </div>
        </form>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-6">
          {error}
        </div>
      )}

      {/* Results */}
      {results.length > 0 && (
        <div>
          <div className="mb-4">
            <h2 className="text-xl font-semibold text-gray-900">
              Search Results ({results.length})
            </h2>
          </div>

          <div className="bg-white shadow-md rounded-lg overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Document
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Collection
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Version
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Metadata
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {results.map((result) => (
                  <tr key={result.documentId} className="hover:bg-gray-50">
                    <td className="px-6 py-4">
                      <div>
                        <div className="text-sm font-medium text-gray-900">
                          {result.title}
                        </div>
                        {result.description && (
                          <div className="text-sm text-gray-500 truncate max-w-xs">
                            {result.description}
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {result.collectionName}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${getStatusBadge(result.status)}`}>
                        {result.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      v{result.currentVersion}
                    </td>
                    <td className="px-6 py-4 text-sm">
                      <div className="space-y-1">
                        {result.product && (
                          <div className="text-gray-600">
                            <span className="font-medium">Product:</span> {result.product}
                          </div>
                        )}
                        {result.department && (
                          <div className="text-gray-600">
                            <span className="font-medium">Dept:</span> {result.department}
                          </div>
                        )}
                        {result.tags && result.tags.length > 0 && (
                          <div className="flex flex-wrap gap-1 mt-1">
                            {result.tags.slice(0, 3).map((tag, index) => (
                              <span
                                key={index}
                                className="px-2 py-0.5 bg-blue-100 text-blue-800 rounded text-xs"
                              >
                                {tag}
                              </span>
                            ))}
                            {result.tags.length > 3 && (
                              <span className="text-xs text-gray-500">
                                +{result.tags.length - 3} more
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                      <Link
                        href={`/documents/${result.documentId}`}
                        className="text-blue-600 hover:text-blue-900"
                      >
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* No Results */}
      {!loading && results.length === 0 && hasActiveFilters() && (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg mb-4">No documents found matching your criteria</div>
          <p className="text-gray-400">Try adjusting your search filters</p>
        </div>
      )}

      {/* Initial State */}
      {!loading && results.length === 0 && !hasActiveFilters() && (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg mb-4">Enter search criteria above</div>
          <p className="text-gray-400">Fill in one or more fields to search documents</p>
        </div>
      )}
    </div>
  )
}
