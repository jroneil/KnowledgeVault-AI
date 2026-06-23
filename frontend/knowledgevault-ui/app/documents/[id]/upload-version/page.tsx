'use client'

import { useState, useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '../../../context/AuthContext'

export default function UploadVersionPage() {
  const [file, setFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const { token } = useAuth()
  const router = useRouter()
  const params = useParams()
  const documentId = params.id as string

  useEffect(() => {
    if (!token) {
      router.push('/login')
      return
    }
  }, [token, router])

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

    setUploading(true)
    setError('')
    setSuccess(false)

    try {
      const formData = new FormData()
      formData.append('file', file)

      const response = await fetch(
        `http://localhost:8080/api/v1/documents/${documentId}/versions`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token}`
          },
          body: formData
        }
      )

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.message || 'Upload failed')
      }

      setSuccess(true)
      setFile(null)
      
      // Redirect after 2 seconds
      setTimeout(() => {
        router.push(`/documents/${documentId}/versions`)
      }, 2000)

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-2xl">
      <div className="mb-6">
        <Link href={`/documents/${documentId}/versions`} className="text-blue-600 hover:text-blue-800">
          ← Back to Versions
        </Link>
      </div>

      <h1 className="text-3xl font-bold text-gray-900 mb-2">Upload New Version</h1>
      <p className="text-gray-600 mb-8">Upload a new version of this document</p>

      {error && (
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
          {error}
        </div>
      )}

      {success && (
        <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-4">
          Version uploaded successfully! Redirecting to version history...
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white shadow-md rounded-lg p-8">
        {/* File Upload */}
        <div className="mb-6">
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
                <div className="mt-4">
                  <p className="text-sm text-green-600 font-medium">
                    Selected: {file.name}
                  </p>
                  <p className="text-xs text-gray-500">
                    Size: {(file.size / 1024 / 1024).toFixed(2)} MB
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Info Box */}
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
          <div className="flex">
            <div className="flex-shrink-0">
              <svg className="h-5 w-5 text-blue-400" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
              </svg>
            </div>
            <div className="ml-3">
              <h3 className="text-sm font-medium text-blue-800">Version Information</h3>
              <div className="mt-2 text-sm text-blue-700">
                <p>
                  The new version will be automatically assigned the next version number. 
                  You can set it as the current version from the version history page.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Submit Button */}
        <div className="flex justify-end space-x-4">
          <Link
            href={`/documents/${documentId}/versions`}
            className="px-6 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </Link>
          <button
            type="submit"
            disabled={uploading || !file}
            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
          >
            {uploading ? 'Uploading...' : 'Upload Version'}
          </button>
        </div>
      </form>
    </div>
  )
}