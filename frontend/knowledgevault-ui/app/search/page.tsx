'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../context/AuthContext'
import {
  Collection,
  SearchResult,
  SemanticSearchResponse,
  listCollections,
  runSemanticSearch,
  searchDocuments,
} from '../../lib/api'

export default function SearchPage() {
  const [results, setResults] = useState<SearchResult[]>([])
  const [collections, setCollections] = useState<Collection[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [semanticQuery, setSemanticQuery] = useState('')
  const [semanticResults, setSemanticResults] = useState<SemanticSearchResponse | null>(null)
  const [semanticLoading, setSemanticLoading] = useState(false)
  const [semanticError, setSemanticError] = useState('')
  const { apiFetch, token } = useAuth()
  const router = useRouter()

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
    listCollections(apiFetch)
      .then(data => {
        if (!cancelled) setCollections(data.filter(item => item.isActive))
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

      setResults(await searchDocuments(apiFetch, params))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Search failed')
    } finally {
      setLoading(false)
    }
  }

  const handleSemanticSearch = async (e: React.FormEvent) => {
    e.preventDefault()
    setSemanticLoading(true)
    setSemanticError('')
    setSemanticResults(null)

    try {
      setSemanticResults(await runSemanticSearch(apiFetch, {
        query: semanticQuery,
        limit: 5,
      }))
    } catch (err) {
      setSemanticError(err instanceof Error ? err.message : 'Semantic search failed')
    } finally {
      setSemanticLoading(false)
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

  const hasActiveFilters = () =>
    Boolean(title || collection || product || revision || department || manufacturer || category || tags || status)

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-6 flex items-center justify-between gap-4">
        <Link href="/documents" className="text-blue-600 hover:text-blue-800">
          ← Back to Documents
        </Link>
        <Link href="/ask" className="text-purple-600 hover:text-purple-800">
          Open Ask Page →
        </Link>
      </div>

      <h1 className="text-3xl font-bold text-gray-900 mb-2">Advanced Search</h1>
      <p className="text-gray-600 mb-8">Search documents by metadata or run semantic retrieval against indexed chunks.</p>

      <div className="bg-white shadow-md rounded-lg p-6 mb-6">
        <form onSubmit={handleSearch}>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2">
              <label className="block text-gray-700 text-sm font-bold mb-2">Document Title</label>
              <input
                type="text"
                placeholder="Search by title..."
                value={title}
                onChange={e => setTitle(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">Collection</label>
              <select
                value={collection}
                onChange={e => setCollection(e.target.value)}
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

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">Product</label>
              <input
                type="text"
                placeholder="Product name"
                value={product}
                onChange={e => setProduct(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">Revision</label>
              <input
                type="text"
                placeholder="Revision number"
                value={revision}
                onChange={e => setRevision(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">Department</label>
              <input
                type="text"
                placeholder="Department name"
                value={department}
                onChange={e => setDepartment(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">Manufacturer</label>
              <input
                type="text"
                placeholder="Manufacturer name"
                value={manufacturer}
                onChange={e => setManufacturer(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">Category</label>
              <input
                type="text"
                placeholder="Document category"
                value={category}
                onChange={e => setCategory(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">Status</label>
              <select
                value={status}
                onChange={e => setStatus(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">All Status</option>
                <option value="ACTIVE">Active</option>
                <option value="ARCHIVED">Archived</option>
                <option value="DELETED">Deleted</option>
              </select>
            </div>

            <div className="lg:col-span-3">
              <label className="block text-gray-700 text-sm font-bold mb-2">Tags</label>
              <input
                type="text"
                placeholder="Comma-separated tags (e.g., important, review, q1)"
                value={tags}
                onChange={e => setTags(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-500 mt-1">Separate multiple tags with commas</p>
            </div>
          </div>

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

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-6">
          {error}
        </div>
      )}

      {results.length > 0 && (
        <div>
          <div className="mb-4">
            <h2 className="text-xl font-semibold text-gray-900">Search Results ({results.length})</h2>
          </div>

          <div className="bg-white shadow-md rounded-lg overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Document</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Collection</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Version</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Metadata</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {results.map(result => (
                  <tr key={result.documentId} className="hover:bg-gray-50">
                    <td className="px-6 py-4">
                      <div>
                        <div className="text-sm font-medium text-gray-900">{result.title}</div>
                        {result.description && (
                          <div className="text-sm text-gray-500 truncate max-w-xs">{result.description}</div>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{result.collectionName}</td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${getStatusBadge(result.status)}`}>
                        {result.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">v{result.currentVersion}</td>
                    <td className="px-6 py-4 text-sm">
                      <div className="space-y-1">
                        {result.product && <div className="text-gray-600"><span className="font-medium">Product:</span> {result.product}</div>}
                        {result.department && <div className="text-gray-600"><span className="font-medium">Dept:</span> {result.department}</div>}
                        {result.tags && result.tags.length > 0 && (
                          <div className="flex flex-wrap gap-1 mt-1">
                            {result.tags.slice(0, 3).map((tag, index) => (
                              <span key={index} className="px-2 py-0.5 bg-blue-100 text-blue-800 rounded text-xs">
                                {tag}
                              </span>
                            ))}
                            {result.tags.length > 3 && (
                              <span className="text-xs text-gray-500">+{result.tags.length - 3} more</span>
                            )}
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                      <Link href={`/documents/${result.documentId}`} className="text-blue-600 hover:text-blue-900">
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

      {!loading && results.length === 0 && hasActiveFilters() && (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg mb-4">No documents found matching your criteria</div>
          <p className="text-gray-400">Try adjusting your search filters</p>
        </div>
      )}

      {!loading && results.length === 0 && !hasActiveFilters() && (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg mb-4">Enter search criteria above</div>
          <p className="text-gray-400">Fill in one or more fields to search documents</p>
        </div>
      )}

      <div className="mt-12 grid gap-8 lg:grid-cols-[2fr_1fr]">
        <div className="rounded-lg bg-white p-6 shadow-md">
          <h2 className="mb-2 text-xl font-semibold text-gray-900">Semantic Search</h2>
          <p className="mb-4 text-sm text-gray-600">Search indexed chunks using embeddings.</p>
          <form onSubmit={handleSemanticSearch}>
            <textarea
              value={semanticQuery}
              onChange={e => setSemanticQuery(e.target.value)}
              className="min-h-28 w-full rounded-md border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Ask a semantic search question..."
            />
            <button
              type="submit"
              disabled={semanticLoading || !semanticQuery.trim()}
              className="mt-4 rounded-md bg-blue-600 px-6 py-2 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-400"
            >
              {semanticLoading ? 'Searching...' : 'Run Semantic Search'}
            </button>
          </form>

          {semanticError && (
            <div className="mt-4 rounded border border-red-400 bg-red-100 px-4 py-3 text-red-700">
              {semanticError}
            </div>
          )}

          {semanticResults && (
            <div className="mt-6">
              <div className="mb-3 text-sm text-gray-600">
                {semanticResults.totalResults} results · model {semanticResults.embeddingModel || 'default'} · {semanticResults.searchTimeMs.toFixed(1)} ms
              </div>
              {semanticResults.results.length === 0 ? (
                <div className="text-sm text-gray-500">No semantic matches found.</div>
              ) : (
                <div className="space-y-4">
                  {semanticResults.results.map(result => (
                    <div key={result.chunkId} className="rounded border border-gray-200 p-4">
                      <div className="mb-2 text-xs text-gray-500">
                        Document #{result.documentId}
                        {result.versionId ? ` · Version ID ${result.versionId}` : ''}
                        {result.pageNumber ? ` · Page ${result.pageNumber}` : ''}
                        {typeof result.similarityScore === 'number' ? ` · Score ${result.similarityScore.toFixed(3)}` : ''}
                      </div>
                      <div className="text-sm text-gray-900">{result.content}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="rounded-lg bg-white p-6 shadow-md">
          <h2 className="mb-2 text-xl font-semibold text-gray-900">Ask Questions</h2>
          <p className="mb-4 text-sm text-gray-600">
            Use the dedicated grounded-answer page for single-shot RAG.
          </p>
          <Link
            href="/ask"
            className="inline-flex rounded-md bg-purple-600 px-6 py-2 text-white hover:bg-purple-700"
          >
            Open Ask Page
          </Link>
          <div className="mt-4 text-sm text-gray-500">
            The Ask page uses the real backend RAG endpoint and displays answer citations returned by the server.
          </div>
        </div>
      </div>
    </div>
  )
}
