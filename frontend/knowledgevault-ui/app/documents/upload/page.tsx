'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../../context/AuthContext'

interface Collection {
  id: number
  name: string
  description: string
  isActive: boolean
}

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
  const [file, setFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const { token } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!token) {
      router.push('/login')
      return
    }
    fetchCollections()
  }, [token, router])

  const fetchCollections = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/v1/collections', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      if (!response.ok) {
        throw new Error('Failed to fetch collections')
      }
      
      const data = await response.json()
      setCollections(data.filter((c: Collection) => c.isActive))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred')
    } finally {
      setLoading(false)
    }
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({ ...prev, [name]: value }))
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0])
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!file) {
      setError('Please select a file to upload')
      return
    }

    if (!formData.collectionId) {
      setError('Please select a collection')
      return
    }

    setUploading(true)
    setError('')
    setSuccess(false)

    try {
      const formDataToSend = new FormData()
      formDataToSend.append('file', file)
      formDataToSend.append('collectionId', formData.collectionId)
      formDataToSend.append('title', formData.title)
      if (formData.description) formDataToSend.append('description', formData.description)
      if (formData.product) formDataToSend.append('product', formData.product)
      if (formData.revision) formDataToSend.append('revision', formData.revision)
      if (formData.department) formDataToSend.append('department', formData.department)
      if (formData.manufacturer) formDataToSend.append('manufacturer', formData.manufacturer)
      if (formData.category) formDataToSend.append('category', formData.category)
      if (formData.tags) formDataToSend.append('tags', formData.tags)
      if (formData.effectiveDate) formDataToSend.append('effectiveDate', formData.effectiveDate)

      const response = await fetch('http://localhost:8080/api/v1/documents/upload', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
        },
        body: formDataToSend
      })

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.message || 'Upload failed')
      }

      setSuccess(true)
      // Reset form after successful upload
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
      setFile(null)
      
      // Redirect after 2 seconds
      setTimeout(() => {
        router.push('/documents')
      }, 2000)

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
          ← Back to Documents
        </Link>
      </div>

      <h1 className="text-3xl font-bold text-gray-900 mb-2">Upload Document</h1>
      <p className="text-gray-600 mb-8">Upload a new document to your collection</p>

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
          {error}
        </div>
      )}

      {success && (
        <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-4">
          Document uploaded successfully! Redirecting to documents list...
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white shadow-md rounded-lg p-8">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Collection Selection */}
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

          {/* File Upload */}
          <div className="md:col-span-2">
            <label className="block text-gray-700 text-sm font-bold mb-2">
              File *
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
                    <span>Upload a file</span>
                    <input
                      type="file"
                      className="sr-only"
                      onChange={handleFileChange}
                      accept=".pdf,.doc,.docx,.txt,.html,.csv,.md"
                    />
                  </label>
                  <p className="pl-1">or drag and drop</p>
                </div>
                <p className="text-xs text-gray-500">
                  PDF, DOC, DOCX, TXT, HTML, CSV, Markdown up to 50MB
                </p>
                {file && (
                  <p className="text-sm text-green-600 font-medium mt-2">
                    Selected: {file.name}
                  </p>
                )}
              </div>
            </div>
          </div>

          {/* Document Title */}
          <div className="md:col-span-2">
            <label className="block text-gray-700 text-sm font-bold mb-2">
              Title *
            </label>
            <input
              type="text"
              name="title"
              value={formData.title}
              onChange={handleInputChange}
              required
              placeholder="Enter document title"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {/* Description */}
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

          {/* Product */}
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

          {/* Revision */}
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

          {/* Department */}
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

          {/* Manufacturer */}
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

          {/* Category */}
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

          {/* Effective Date */}
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

          {/* Tags */}
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

        {/* Submit Button */}
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
            {uploading ? 'Uploading...' : 'Upload Document'}
          </button>
        </div>
      </form>
    </div>
  )
}