'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../context/AuthContext'

interface Document {
  id: number
  collectionId: number
  title: string
  description: string
  status: string
  currentVersion: number
  createdAt: string
  updatedAt: string
}

export default function DocumentsPage() {
  const [documents, setDocuments] = useState<Document[]>([])
  const [filteredDocuments, setFilteredDocuments] = useState<Document[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const { token } = useAuth()
  const router = useRouter()

  // Filter states
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [collectionFilter, setCollectionFilter] = useState('ALL')

  useEffect(() => {
    if (!token) {
      router.push('/login')
      return
    }
    fetchDocuments()
  }, [token, router])

  useEffect(() => {
    filterDocuments()
  }, [documents, searchTerm, statusFilter, collectionFilter])

  const fetchDocuments = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/v1/documents', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      if (!response.ok) {
        throw new Error('Failed to fetch documents')
      }
      
      const data = await response.json()
      setDocuments(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred')
    } finally {
      setLoading(false)
    }
  }

  const filterDocuments = () => {
    let filtered = documents

    // Filter by search term
    if (searchTerm) {
      filtered = filtered.filter(doc =>
        doc.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (doc.description && doc.description.toLowerCase().includes(searchTerm.toLowerCase()))
      )
    }

    // Filter by status
    if (statusFilter !== 'ALL') {
      filtered = filtered.filter(doc => doc.status === statusFilter)
    }

    // Filter by collection (simplified - in real app would have collection names)
    if (collectionFilter !== 'ALL') {
      filtered = filtered.filter(doc => doc.collectionId === parseInt(collectionFilter))
    }

    setFilteredDocuments(filtered)
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

  const handleDownload = async (documentId: number) => {
    try {
      const response = await fetch(`http://localhost:8080/api/v1/documents/${documentId}/download`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })

      if (!response.ok) {
        throw new Error('Failed to download document')
      }

      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `document_${documentId}.pdf`
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Download failed')
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading documents...</div>
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
          <h1 className="text-3xl font-bold text-gray-900">Documents</h1>
          <p className="text-gray-600 mt-2">Manage and search your documents</p>
        </div>
        <Link
          href="/documents/upload"
          className="bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors"
        >
          Upload Document
        </Link>
      </div>

      {/* Search and Filters */}
      <div className="bg-white shadow-md rounded-lg p-6 mb-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Search
            </label>
            <input
              type="text"
              placeholder="Search documents..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          
          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Status
            </label>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Status</option>
              <option value="ACTIVE">Active</option>
              <option value="ARCHIVED">Archived</option>
              <option value="DELETED">Deleted</option>
            </select>
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Collection
            </label>
            <select
              value={collectionFilter}
              onChange={(e) => setCollectionFilter(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Collections</option>
              {/* In real app, would fetch collections dynamically */}
              <option value="1">Collection 1</option>
              <option value="2">Collection 2</option>
            </select>
          </div>
        </div>
      </div>

      {/* Results Count */}
      <div className="mb-4 text-gray-600">
        Showing {filteredDocuments.length} of {documents.length} documents
      </div>

      {filteredDocuments.length === 0 ? (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg mb-4">No documents found</div>
          <p className="text-gray-400 mb-6">
            {documents.length === 0 
              ? 'Upload your first document to get started'
              : 'Try adjusting your search or filters'}
          </p>
          {documents.length === 0 && (
            <Link
              href="/documents/upload"
              className="bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors"
            >
              Upload Document
            </Link>
          )}
        </div>
      ) : (
        <div className="bg-white shadow-md rounded-lg overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Document
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Version
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Created
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredDocuments.map((document) => (
                <tr key={document.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div>
                      <div className="text-sm font-medium text-gray-900">
                        {document.title}
                      </div>
                      {document.description && (
                        <div className="text-sm text-gray-500 truncate max-w-xs">
                          {document.description}
                        </div>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full ${getStatusBadge(document.status)}`}>
                      {document.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    v{document.currentVersion}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {new Date(document.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium space-x-2">
                    <button
                      onClick={() => router.push(`/documents/${document.id}`)}
                      className="text-blue-600 hover:text-blue-900"
                    >
                      View
                    </button>
                    <button
                      onClick={() => handleDownload(document.id)}
                      className="text-green-600 hover:text-green-900"
                    >
                      Download
                    </button>
                    <Link
                      href={`/documents/${document.id}/versions`}
                      className="text-purple-600 hover:text-purple-900"
                    >
                      Versions
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Link to advanced search */}
      <div className="mt-6 text-center">
        <Link
          href="/search"
          className="text-blue-600 hover:text-blue-800 font-medium"
        >
          Advanced Search →
        </Link>
      </div>
    </div>
  )
}