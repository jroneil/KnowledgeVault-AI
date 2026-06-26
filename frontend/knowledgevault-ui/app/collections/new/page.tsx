'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuth } from '../../context/AuthContext'
import { createCollection } from '../../../lib/api'

export default function NewCollectionPage() {
  const router = useRouter()
  const { apiFetch } = useAuth()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError('')

    try {
      const collection = await createCollection(apiFetch, { name, description: description || undefined })
      router.push(`/documents?collectionId=${collection.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create collection')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6">
        <Link href="/collections" className="text-blue-600 hover:text-blue-800">
          ← Back to Collections
        </Link>
      </div>

      <h1 className="mb-2 text-3xl font-bold text-gray-900">Create Collection</h1>
      <p className="mb-8 text-gray-600">Create a real collection in the backend for organizing documents.</p>

      {error && (
        <div className="mb-4 rounded border border-red-400 bg-red-100 px-4 py-3 text-red-700">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="rounded-lg bg-white p-8 shadow-md">
        <div className="mb-6">
          <label className="mb-2 block text-sm font-bold text-gray-700">Name *</label>
          <input
            type="text"
            required
            value={name}
            onChange={event => setName(event.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="mb-6">
          <label className="mb-2 block text-sm font-bold text-gray-700">Description</label>
          <textarea
            rows={4}
            value={description}
            onChange={event => setDescription(event.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="flex justify-end gap-4">
          <Link
            href="/collections"
            className="rounded-md border border-gray-300 px-6 py-2 text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </Link>
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-blue-600 px-6 py-2 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-400"
          >
            {submitting ? 'Creating...' : 'Create Collection'}
          </button>
        </div>
      </form>
    </div>
  )
}
