'use client'

import { useCallback, useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../../context/AuthContext'
import {
  DocumentMetadata,
  DocumentSummary,
  IngestionJob,
  getDocument,
  getDocumentMetadata,
  listDocumentIngestionJobs,
} from '../../../lib/api'

export default function DocumentDetailPage() {
  const [document, setDocument] = useState<DocumentSummary | null>(null)
  const [metadata, setMetadata] = useState<DocumentMetadata | null>(null)
  const [jobs, setJobs] = useState<IngestionJob[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const { apiFetch, token } = useAuth()
  const router = useRouter()
  const params = useParams()
  const documentId = params.id as string

  const fetchDocumentData = useCallback(async () => {
    try {
      const [docData, metaData, jobData] = await Promise.all([
        getDocument(apiFetch, documentId),
        getDocumentMetadata(apiFetch, documentId),
        listDocumentIngestionJobs(apiFetch, documentId).catch(() => []),
      ])
      setDocument(docData)
      setMetadata(metaData)
      setJobs(jobData)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred')
    } finally {
      setLoading(false)
    }
  }, [apiFetch, documentId])

  useEffect(() => {
    if (!token) {
      router.push('/login')
      return
    }
    const timeoutId = window.setTimeout(() => {
      void fetchDocumentData()
    }, 0)
    return () => window.clearTimeout(timeoutId)
  }, [token, router, fetchDocumentData])

  const handleDownload = async () => {
    try {
      const response = await apiFetch(`/api/v1/documents/${documentId}/download`)

      if (!response.ok) {
        throw new Error('Failed to download document')
      }

      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = window.document.createElement('a')
      a.href = url
      a.download = `${document?.title || 'document'}.pdf`
      window.document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      window.document.body.removeChild(a)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Download failed')
    }
  }

  const handleArchive = async () => {
    if (!confirm('Are you sure you want to archive this document?')) {
      return
    }

    try {
      const response = await apiFetch(`/api/v1/documents/${documentId}`, {
        method: 'PUT',
        body: JSON.stringify({
          ...document,
          status: 'ARCHIVED'
        })
      })

      if (!response.ok) {
        throw new Error('Failed to archive document')
      }

      router.push('/documents')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Archive failed')
    }
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

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading document...</div>
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

  if (!document) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-gray-600">Document not found</div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-6xl">
      <div className="mb-6">
        <Link href="/documents" className="text-blue-600 hover:text-blue-800">
          ← Back to Documents
        </Link>
      </div>

      {/* Header */}
      <div className="flex justify-between items-start mb-8">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">{document.title}</h1>
          <div className="flex items-center space-x-4">
            <span className={`px-3 py-1 rounded-full text-xs font-medium ${getStatusBadge(document.status)}`}>
              {document.status}
            </span>
            <span className="text-gray-600 text-sm">Version {document.currentVersion}</span>
          </div>
        </div>
        <div className="flex space-x-3">
          <button
            onClick={handleDownload}
            className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 transition-colors"
          >
            Download
          </button>
          <button
            onClick={() => router.push(`/documents/${documentId}/upload-version`)}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors"
          >
            Upload New Version
          </button>
          {document.status === 'ACTIVE' && (
            <button
              onClick={handleArchive}
              className="bg-yellow-600 text-white px-4 py-2 rounded-lg hover:bg-yellow-700 transition-colors"
            >
              Archive
            </button>
          )}
        </div>
      </div>

      {/* Description */}
      {document.description && (
        <div className="bg-white shadow-md rounded-lg p-6 mb-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-3">Description</h2>
          <p className="text-gray-600">{document.description}</p>
        </div>
      )}

      {/* Metadata */}
      <div className="bg-white shadow-md rounded-lg p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Document Metadata</h2>
        
        {metadata ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Product
              </label>
              <p className="text-gray-900">{metadata.product || 'N/A'}</p>
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Revision
              </label>
              <p className="text-gray-900">{metadata.revision || 'N/A'}</p>
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Department
              </label>
              <p className="text-gray-900">{metadata.department || 'N/A'}</p>
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Manufacturer
              </label>
              <p className="text-gray-900">{metadata.manufacturer || 'N/A'}</p>
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Category
              </label>
              <p className="text-gray-900">{metadata.category || 'N/A'}</p>
            </div>

            <div>
              <label className="block text-gray-700 text-sm font-bold mb-2">
                Effective Date
              </label>
              <p className="text-gray-900">
                {metadata.effectiveDate ? new Date(metadata.effectiveDate).toLocaleDateString() : 'N/A'}
              </p>
            </div>

            {metadata.tags && metadata.tags.length > 0 && (
              <div className="md:col-span-2">
                <label className="block text-gray-700 text-sm font-bold mb-2">
                  Tags
                </label>
                <div className="flex flex-wrap gap-2">
                  {metadata.tags.map((tag, index) => (
                    <span
                      key={index}
                      className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <p className="text-gray-500">No metadata available</p>
        )}
      </div>

      {/* Timestamps */}
      <div className="bg-white shadow-md rounded-lg p-6 mb-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Timestamps</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Created At
            </label>
            <p className="text-gray-900">{new Date(document.createdAt).toLocaleString()}</p>
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Updated At
            </label>
            <p className="text-gray-900">{new Date(document.updatedAt).toLocaleString()}</p>
          </div>
        </div>
      </div>

      {/* Version History Link */}
      <div className="bg-white shadow-md rounded-lg p-6">
        <div className="flex justify-between items-center">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Version History</h2>
            <p className="text-gray-600 text-sm mt-1">View and manage all versions of this document</p>
          </div>
          <Link
            href={`/documents/${documentId}/versions`}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition-colors"
          >
            View Versions
          </Link>
        </div>
      </div>

      <div className="mt-6 bg-white shadow-md rounded-lg p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Ingestion Status</h2>
        {jobs.length === 0 ? (
          <p className="text-gray-500">No ingestion jobs reported for this document yet.</p>
        ) : (
          <div className="space-y-3">
            {jobs.map(job => (
              <div key={job.id} className="rounded-lg border border-gray-200 p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-semibold text-gray-900">
                      Job #{job.id} · Version ID {job.versionId}
                    </div>
                    <div className="text-sm text-gray-600">
                      Status: {job.status} · Progress: {job.progressPercent ?? 0}%
                    </div>
                  </div>
                  <div className="text-right text-xs text-gray-500">
                    {job.updatedAt ? `Updated ${new Date(job.updatedAt).toLocaleString()}` : 'Pending update'}
                  </div>
                </div>
                {(job.errorMessage || job.lastErrorMessage) && (
                  <div className="mt-2 text-sm text-red-600">
                    {job.errorMessage || job.lastErrorMessage}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
