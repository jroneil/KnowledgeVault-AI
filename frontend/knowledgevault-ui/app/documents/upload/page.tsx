'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../../context/AuthContext'
import {
  BulkUploadResponse,
  Collection,
  IngestionJob,
  bulkUploadDocuments,
  listCollections,
  listDocumentIngestionJobs,
  uploadDocument,
} from '../../../lib/api'

export default function DocumentUploadPage() {
  const [collections, setCollections] = useState<Collection[]>([])
  const [formData, setFormData] = useState({
    collectionId: '',
    title: '',
    description: '',
    product: '',
    revision: '',
    department: '',
    manufacturer: '',
    category: '',
    tags: '',
    effectiveDate: ''
  })
  const [files, setFiles] = useState<File[]>([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [uploadedDocumentId, setUploadedDocumentId] = useState<number | null>(null)
  const [ingestionJobs, setIngestionJobs] = useState<IngestionJob[]>([])
  const [bulkSummary, setBulkSummary] = useState<BulkUploadResponse | null>(null)
  const { apiFetch, token } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!token) {
      router.push('/login')
      return
    }
    let cancelled = false
    listCollections(apiFetch)
      .then(data => {
        if (!cancelled) setCollections(data.filter(collection => collection.isActive))
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

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({ ...prev, [name]: value }))
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFiles(e.target.files ? Array.from(e.target.files) : [])
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (files.length === 0) {
      setError('Please select at least one file to upload')
      return
    }

    if (!formData.collectionId) {
      setError('Please select a collection')
      return
    }

    if (files.length === 1 && !formData.title.trim()) {
      setError('Please enter a title for single-document upload')
      return
    }

    setUploading(true)
    setError('')
    setSuccess(false)
    setUploadedDocumentId(null)
    setIngestionJobs([])
    setBulkSummary(null)

    try {
      const metadataBlob = new Blob(
        [JSON.stringify({
          ...formData,
          collectionId: Number(formData.collectionId)
        })],
        { type: 'application/json' }
      )

      if (files.length === 1) {
        const formDataToSend = new FormData()
        formDataToSend.append('file', files[0])
        formDataToSend.append('metadata', metadataBlob)

        const uploadResponse = await uploadDocument(apiFetch, formDataToSend)
        setSuccess(true)
        setUploadedDocumentId(uploadResponse.documentId)
        setIngestionJobs(await listDocumentIngestionJobs(apiFetch, uploadResponse.documentId).catch(() => []))
        setTimeout(() => {
          router.push('/documents')
        }, 2000)
      } else {
        const formDataToSend = new FormData()
        files.forEach(file => formDataToSend.append('files', file))
        formDataToSend.append('metadata', metadataBlob)

        const response = await bulkUploadDocuments(apiFetch, formDataToSend)
        setSuccess(response.succeededCount > 0)
        setBulkSummary(response)
      }

      setFormData({
        collectionId: '',
        title: '',
        description: '',
        product: '',
        revision: '',
        department: '',
        manufacturer: '',
        category: '',
        tags: '',
        effectiveDate: ''
      })
      setFiles([])
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading...</div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      <div className="mb-6">
        <Link href="/documents" className="text-blue-600 hover:text-blue-800">
          Back to Documents
        </Link>
      </div>

      <h1 className="text-3xl font-bold text-gray-900 mb-2">Upload Documents</h1>
      <p className="text-gray-600 mb-8">Upload one or more documents to your collection</p>

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
          {error}
        </div>
      )}

      {success && (
        <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-4">
          {bulkSummary
            ? `Bulk upload completed. ${bulkSummary.succeededCount} succeeded, ${bulkSummary.failedCount} failed, ${bulkSummary.needsReviewCount} need review.`
            : 'Document uploaded successfully! Redirecting to documents list...'}
        </div>
      )}

      {success && uploadedDocumentId && (
        <div className="bg-blue-50 border border-blue-200 text-blue-900 px-4 py-3 rounded mb-4">
          <div className="font-medium">Ingestion status</div>
          {ingestionJobs.length === 0 ? (
            <div className="text-sm mt-1">No ingestion job information is available yet.</div>
          ) : (
            <div className="mt-2 space-y-2 text-sm">
              {ingestionJobs.map(job => (
                <div key={job.id}>
                  Job #{job.id}: {job.status} ({job.progressPercent ?? 0}%)
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {bulkSummary && (
        <div className="bg-blue-50 border border-blue-200 text-blue-900 px-4 py-3 rounded mb-4">
          <div className="font-medium">Bulk upload summary</div>
          <div className="text-sm mt-1">
            Processed {bulkSummary.processedCount} files. {bulkSummary.succeededCount} succeeded, {bulkSummary.failedCount} failed, {bulkSummary.needsReviewCount} need review.
          </div>
          <div className="mt-3 space-y-2 text-sm">
            {bulkSummary.results.map(result => (
              <div key={`${result.fileName}-${result.documentId ?? result.error ?? 'result'}`}>
                <span className={result.success ? 'text-green-700' : 'text-red-700'}>
                  {result.success ? 'Success' : 'Failed'}
                </span>{' '}
                - {result.fileName}
                {result.title ? ` (${result.title})` : ''}
                {result.needsReview ? ' - needs review' : ''}
                {result.error ? ` - ${result.error}` : ''}
              </div>
            ))}
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white shadow-md rounded-lg p-8">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="md:col-span-2">
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Collection *
            </label>
            <select
              name="collectionId"
              value={formData.collectionId}
              onChange={handleInputChange}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Select a collection</option>
              {collections.map(collection => (
                <option key={collection.id} value={collection.id}>
                  {collection.name}
                </option>
              ))}
            </select>
          </div>

          <div className="md:col-span-2">
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Files *
            </label>
            <div className="mt-1 flex justify-center px-6 pt-5 pb-6 border-2 border-gray-300 border-dashed rounded-md hover:border-blue-500 transition-colors">
              <div className="space-y-1 text-center">
                <svg
                  className="mx-auto h-12 w-12 text-gray-400"
                  stroke="currentColor"
                  fill="none"
                  viewBox="0 0 48 48"
                >
                  <path
                    d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02"
                    strokeWidth={2}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
                <div className="flex text-sm text-gray-600">
                  <label className="relative cursor-pointer bg-white rounded-md font-medium text-blue-600 hover:text-blue-500 focus-within:outline-none">
                    <span>Upload files</span>
                    <input
                      type="file"
                      className="sr-only"
                      onChange={handleFileChange}
                      multiple
                      accept=".pdf,.doc,.docx,.txt,.html,.csv,.md"
                    />
                  </label>
                  <p className="pl-1">or drag and drop</p>
                </div>
                <p className="text-xs text-gray-500">
                  PDF, DOC, DOCX, TXT, HTML, CSV, Markdown up to 50MB
                </p>
                {files.length > 0 && (
                  <div className="text-sm text-green-600 font-medium mt-2">
                    <p>Selected {files.length} file{files.length === 1 ? '' : 's'}:</p>
                    <ul className="mt-1 space-y-1">
                      {files.slice(0, 5).map(file => (
                        <li key={file.name}>{file.name}</li>
                      ))}
                      {files.length > 5 && <li>...and {files.length - 5} more</li>}
                    </ul>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="md:col-span-2">
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Title {files.length <= 1 ? '*' : '(optional shared override)'}
            </label>
            <input
              type="text"
              name="title"
              value={formData.title}
              onChange={handleInputChange}
              required={files.length <= 1}
              placeholder={files.length <= 1 ? 'Enter document title' : 'Optional shared title for all selected files'}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="md:col-span-2">
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Description
            </label>
            <textarea
              name="description"
              value={formData.description}
              onChange={handleInputChange}
              rows={3}
              placeholder="Enter document description (optional)"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Product
            </label>
            <input
              type="text"
              name="product"
              value={formData.product}
              onChange={handleInputChange}
              placeholder="Product name"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Revision
            </label>
            <input
              type="text"
              name="revision"
              value={formData.revision}
              onChange={handleInputChange}
              placeholder="Revision number"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Department
            </label>
            <input
              type="text"
              name="department"
              value={formData.department}
              onChange={handleInputChange}
              placeholder="Department name"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Manufacturer
            </label>
            <input
              type="text"
              name="manufacturer"
              value={formData.manufacturer}
              onChange={handleInputChange}
              placeholder="Manufacturer name"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Category
            </label>
            <input
              type="text"
              name="category"
              value={formData.category}
              onChange={handleInputChange}
              placeholder="Document category"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div>
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Effective Date
            </label>
            <input
              type="date"
              name="effectiveDate"
              value={formData.effectiveDate}
              onChange={handleInputChange}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="md:col-span-2">
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Tags
            </label>
            <input
              type="text"
              name="tags"
              value={formData.tags}
              onChange={handleInputChange}
              placeholder="Comma-separated tags (e.g., important, review, q1)"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-xs text-gray-500 mt-1">
              Separate multiple tags with commas
            </p>
          </div>
        </div>

        <div className="mt-8 flex justify-end space-x-4">
          <Link
            href="/documents"
            className="px-6 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </Link>
          <button
            type="submit"
            disabled={uploading}
            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
          >
            {uploading ? 'Uploading...' : files.length > 1 ? 'Upload Documents' : 'Upload Document'}
          </button>
        </div>
      </form>
    </div>
  )
}
