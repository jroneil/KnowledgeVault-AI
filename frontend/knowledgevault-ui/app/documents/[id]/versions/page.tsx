'use client'

import { useCallback, useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../../../context/AuthContext'

interface Version {
  id: number
  documentId: number
  versionNumber: number
  fileName: string
  fileSize: number
  mimeType: string
  uploadedBy: string
  uploadDate: string
  isCurrent: boolean
}

interface Document {
  id: number
  title: string
  collectionId: number
}

export default function DocumentVersionsPage() {
  const [versions, setVersions] = useState<Version[]>([])
  const [document, setDocument] = useState<Document | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const { apiFetch, token } = useAuth()
  const router = useRouter()
  const params = useParams()
  const documentId = params.id as string

  const fetchVersionData = useCallback(async () => {
    try {
      const docResponse = await apiFetch(`/api/v1/documents/${documentId}`)

      if (docResponse.ok) {
        const docData = await docResponse.json()
        setDocument(docData)
      }

      const versionsResponse = await apiFetch(`/api/v1/documents/${documentId}/versions`)

      if (!versionsResponse.ok) {
        throw new Error('Failed to fetch versions')
      }

      const versionsData = await versionsResponse.json()
      setVersions(versionsData)
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
      void fetchVersionData()
    }, 0)
    return () => window.clearTimeout(timeoutId)
  }, [token, router, fetchVersionData])

  const handleDownload = async (versionNumber: number) => {
    try {
      const response = await apiFetch(
        `/api/v1/documents/${documentId}/versions/${versionNumber}/download`
      )

      if (!response.ok) {
        throw new Error('Failed to download version')
      }

      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = window.document.createElement('a')
      a.href = url
      a.download = `${document?.title || 'document'}_v${versionNumber}.pdf`
      window.document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      window.document.body.removeChild(a)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Download failed')
    }
  }

  const handleSetCurrent = async (versionNumber: number) => {
    if (!confirm(`Are you sure you want to set version ${versionNumber} as the current version?`)) {
      return
    }

    try {
      const response = await apiFetch(
        `/api/v1/documents/${documentId}/versions/${versionNumber}/set-current`,
        {
          method: 'PUT'
        }
      )

      if (!response.ok) {
        throw new Error('Failed to set current version')
      }

      // Refresh version data
      void fetchVersionData()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Operation failed')
    }
  }

  const handleDelete = async (versionId: number) => {
    if (!confirm('Are you sure you want to delete this version? This action cannot be undone.')) {
      return
    }

    try {
      const response = await apiFetch(
        `/api/v1/documents/${documentId}/versions/${versionId}`,
        {
          method: 'DELETE'
        }
      )

      if (!response.ok) {
        throw new Error('Failed to delete version')
      }

      // Refresh version data
      void fetchVersionData()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  const handleUploadVersion = () => {
    router.push(`/documents/${documentId}/upload-version`)
  }

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 Bytes'
    const k = 1024
    const sizes = ['Bytes', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
  }

  const getMimeTypeIcon = (mimeType: string) => {
    if (mimeType.includes('pdf')) return '📄'
    if (mimeType.includes('word')) return '📝'
    if (mimeType.includes('text')) return '📃'
    if (mimeType.includes('html')) return '🌐'
    return '📁'
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading version history...</div>
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
    <div className="container mx-auto px-4 py-8 max-w-6xl">
      <div className="mb-6">
        <Link href={`/documents/${documentId}`} className="text-blue-600 hover:text-blue-800">
          ← Back to Document
        </Link>
      </div>

      <div className="flex justify-between items-center mb-8">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 mb-2">
            Version History
          </h1>
          <p className="text-gray-600">
            {document ? `Document: ${document.title}` : 'Loading document...'}
          </p>
        </div>
        <button
          onClick={handleUploadVersion}
          className="bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors"
        >
          Upload New Version
        </button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-6">
          {error}
        </div>
      )}

      {versions.length === 0 ? (
        <div className="text-center py-12">
          <div className="text-gray-500 text-lg mb-4">No versions found</div>
          <p className="text-gray-400 mb-6">
            Upload the first version of this document
          </p>
          <button
            onClick={handleUploadVersion}
            className="bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors"
          >
            Upload Version
          </button>
        </div>
      ) : (
        <div className="bg-white shadow-md rounded-lg overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Version
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  File
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Size
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Uploaded By
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Upload Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {versions.map((version) => (
                <tr
                  key={version.id}
                  className={`hover:bg-gray-50 ${version.isCurrent ? 'bg-green-50' : ''}`}
                >
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      <span className="text-2xl mr-2">
                        {getMimeTypeIcon(version.mimeType)}
                      </span>
                      <span className="text-lg font-semibold text-gray-900">
                        v{version.versionNumber}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {version.fileName}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {formatFileSize(version.fileSize)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {version.uploadedBy}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {new Date(version.uploadDate).toLocaleString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    {version.isCurrent ? (
                      <span className="px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                        Current
                      </span>
                    ) : (
                      <span className="px-3 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-gray-100 text-gray-800">
                        Historical
                      </span>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium space-x-2">
                    <button
                      onClick={() => handleDownload(version.versionNumber)}
                      className="text-green-600 hover:text-green-900"
                      title="Download"
                    >
                      Download
                    </button>
                    {!version.isCurrent && (
                      <>
                        <span className="text-gray-300">|</span>
                        <button
                          onClick={() => handleSetCurrent(version.versionNumber)}
                          className="text-blue-600 hover:text-blue-900"
                          title="Set as Current"
                        >
                          Set Current
                        </button>
                        <span className="text-gray-300">|</span>
                        <button
                          onClick={() => handleDelete(version.id)}
                          className="text-red-600 hover:text-red-900"
                          title="Delete"
                        >
                          Delete
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Version Statistics */}
      {versions.length > 0 && (
        <div className="mt-6 bg-white shadow-md rounded-lg p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            Version Statistics
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-blue-50 rounded-lg p-4">
              <div className="text-2xl font-bold text-blue-600">
                {versions.length}
              </div>
              <div className="text-sm text-gray-600">Total Versions</div>
            </div>
            <div className="bg-green-50 rounded-lg p-4">
              <div className="text-2xl font-bold text-green-600">
                {Math.max(...versions.map(v => v.versionNumber))}
              </div>
              <div className="text-sm text-gray-600">Latest Version</div>
            </div>
            <div className="bg-purple-50 rounded-lg p-4">
              <div className="text-2xl font-bold text-purple-600">
                {formatFileSize(versions.reduce((sum, v) => sum + v.fileSize, 0))}
              </div>
              <div className="text-sm text-gray-600">Total Storage Used</div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
