export type ApiFetch = (url: string, options?: RequestInit) => Promise<Response>

export interface Collection {
  id: number
  name: string
  description: string | null
  isActive: boolean
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface DocumentSummary {
  id: number
  collectionId: number
  title: string
  description: string
  status: string
  currentVersion: number
  createdAt: string
  updatedAt: string
}

export interface DocumentMetadata {
  id: number
  documentId: number
  product: string
  revision: string
  department: string
  manufacturer: string
  tags: string[]
  category: string
  effectiveDate: string
  createdAt: string
  updatedAt: string
}

export interface VersionSummary {
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

export interface SearchResult {
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

export interface SemanticChunkResult {
  chunkId: number
  documentId: number
  versionId?: number
  chunkIndex: number
  content: string
  pageNumber?: number
  sectionName?: string
  tokenCount?: number
  similarityScore: number
  createdAt?: string
}

export interface SemanticSearchResponse {
  query: string
  totalResults: number
  results: SemanticChunkResult[]
  queryEmbeddingDimension: number
  embeddingModel?: string
  embeddingModelVersion?: string
  searchTimeMs: number
}

export interface RagContext {
  chunkId: number
  documentId: number
  documentTitle?: string
  versionId?: number
  versionNumber?: number
  revision?: string
  product?: string
  category?: string
  tags?: string[]
  chunkIndex?: number
  content: string
  pageNumber?: number
  sourcePageFrom?: number
  sourcePageTo?: number
  sourceLabel?: string
  similarityScore?: number
}

export interface RagCitation {
  label: string
  documentId: number
  documentTitle?: string
  versionId?: number
  versionNumber?: number
  revision?: string
  pageNumber?: number
  sourcePageFrom?: number
  sourcePageTo?: number
  chunkId?: number
  chunkIndex?: number
  rank?: number
  similarityScore?: number
  sourceLabel?: string
}

export interface RagResponse {
  query: string
  answer: string | null
  contexts: RagContext[]
  citations: RagCitation[]
  totalContexts: number
  modelUsed?: string
  embeddingModel?: string
  processingTimeMs: number
}

export interface SearchStats {
  totalDocuments: number
  totalChunks: number
  totalEmbeddings: number
  embeddingModels: string[]
  llmModels: string[]
}

export interface IngestionWarning {
  id?: number
  warningCode?: string
  warningMessage?: string
  severity?: string
}

export interface IngestionJob {
  id: number
  documentId: number
  versionId: number
  status: string
  progressPercent: number
  attemptCount: number
  embeddingModel?: string
  llmModel?: string
  chunkCount: number
  embeddingCount: number
  errorCode?: string
  errorMessage?: string
  nextAttemptAt?: string
  retryable?: boolean
  lastErrorCode?: string
  lastErrorMessage?: string
  cancellationRequested?: boolean
  warnings?: IngestionWarning[]
  createdAt?: string
  startedAt?: string
  completedAt?: string
  updatedAt?: string
}

export interface UserSessionRecord {
  id: number
  username: string
  email: string
  roles?: string[]
}

export interface DocumentUploadResponse {
  documentId: number
  collectionId: number
  title: string
  currentVersion: number
  versionId: number
  fileName: string
  fileSize: number
  uploadedAt: string
  message: string
}

export interface VersionUploadResponse {
  versionId: number
  documentId: number
  versionNumber: number
  fileName: string
  fileSize: number
  uploadDate: string
  message: string
}

export function getApiUrl(): string {
  return process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, '') || ''
}

export async function parseJsonOrThrow<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (!response.ok) {
    throw await toApiError(response, fallbackMessage)
  }
  return response.json() as Promise<T>
}

export async function toApiError(response: Response, fallbackMessage: string): Promise<Error> {
  try {
    const data = await response.json()
    const message = data.message || data.error || data.detail || fallbackMessage
    return new Error(message)
  } catch {
    return new Error(fallbackMessage)
  }
}

export async function listCollections(apiFetch: ApiFetch, activeOnly = false): Promise<Collection[]> {
  const response = await apiFetch(`/api/v1/collections?activeOnly=${activeOnly}`)
  const data = await parseJsonOrThrow<{ collections: Collection[] }>(response, 'Failed to fetch collections')
  return data.collections
}

export async function createCollection(
  apiFetch: ApiFetch,
  payload: { name: string; description?: string }
): Promise<Collection> {
  const response = await apiFetch('/api/v1/collections', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  return parseJsonOrThrow<Collection>(response, 'Failed to create collection')
}

export async function listDocuments(
  apiFetch: ApiFetch,
  params: { collectionId?: number; status?: string; page?: number; size?: number } = {}
): Promise<{ documents: DocumentSummary[]; totalElements: number }> {
  const searchParams = new URLSearchParams()
  if (params.collectionId != null) searchParams.set('collectionId', String(params.collectionId))
  if (params.status) searchParams.set('status', params.status)
  searchParams.set('page', String(params.page ?? 0))
  searchParams.set('size', String(params.size ?? 100))
  const response = await apiFetch(`/api/v1/documents?${searchParams.toString()}`)
  return parseJsonOrThrow<{ documents: DocumentSummary[]; totalElements: number }>(
    response,
    'Failed to fetch documents'
  )
}

export async function getDocument(apiFetch: ApiFetch, documentId: string | number): Promise<DocumentSummary> {
  const response = await apiFetch(`/api/v1/documents/${documentId}`)
  return parseJsonOrThrow<DocumentSummary>(response, 'Failed to fetch document')
}

export async function getDocumentMetadata(apiFetch: ApiFetch, documentId: string | number): Promise<DocumentMetadata | null> {
  const response = await apiFetch(`/api/v1/documents/${documentId}/metadata`)
  if (response.status === 404) return null
  return parseJsonOrThrow<DocumentMetadata>(response, 'Failed to fetch document metadata')
}

export async function listVersions(apiFetch: ApiFetch, documentId: string | number): Promise<VersionSummary[]> {
  const response = await apiFetch(`/api/v1/documents/${documentId}/versions`)
  return parseJsonOrThrow<VersionSummary[]>(response, 'Failed to fetch versions')
}

export async function uploadDocument(apiFetch: ApiFetch, formData: FormData): Promise<DocumentUploadResponse> {
  const response = await apiFetch('/api/v1/documents/upload', { method: 'POST', body: formData })
  return parseJsonOrThrow<DocumentUploadResponse>(response, 'Upload failed')
}

export async function uploadVersion(
  apiFetch: ApiFetch,
  documentId: string | number,
  formData: FormData
): Promise<VersionUploadResponse> {
  const response = await apiFetch(`/api/v1/documents/${documentId}/versions`, {
    method: 'POST',
    body: formData,
  })
  return parseJsonOrThrow<VersionUploadResponse>(response, 'Upload failed')
}

export async function listDocumentIngestionJobs(apiFetch: ApiFetch, documentId: string | number): Promise<IngestionJob[]> {
  const response = await apiFetch(`/api/v1/documents/${documentId}/ingestion-jobs`)
  const data = await parseJsonOrThrow<{ jobs: IngestionJob[] }>(response, 'Failed to fetch ingestion jobs')
  return data.jobs
}

export async function searchDocuments(apiFetch: ApiFetch, params: URLSearchParams): Promise<SearchResult[]> {
  const response = await apiFetch(`/api/v1/search?${params.toString()}`)
  return parseJsonOrThrow<SearchResult[]>(response, 'Search failed')
}

export async function runSemanticSearch(
  apiFetch: ApiFetch,
  payload: { query: string; limit: number; threshold?: number }
): Promise<SemanticSearchResponse> {
  const response = await apiFetch('/api/v1/semantic-search', {
    method: 'POST',
    body: JSON.stringify({
      query: payload.query,
      limit: payload.limit,
      threshold: payload.threshold ?? 0,
    }),
  })
  return parseJsonOrThrow<SemanticSearchResponse>(response, 'Semantic search failed')
}

export async function runRagQuery(
  apiFetch: ApiFetch,
  payload: { query: string; topK: number; maxTokens: number; temperature?: number }
): Promise<RagResponse> {
  const response = await apiFetch('/api/v1/semantic-search/rag', {
    method: 'POST',
    body: JSON.stringify({
      query: payload.query,
      topK: payload.topK,
      maxTokens: payload.maxTokens,
      temperature: payload.temperature ?? 0,
    }),
  })
  return parseJsonOrThrow<RagResponse>(response, 'RAG request failed')
}

export async function getDocumentOverviewStats(apiFetch: ApiFetch): Promise<{ totalDocuments: number }> {
  const response = await apiFetch('/api/v1/documents/stats/overview')
  return parseJsonOrThrow<{ totalDocuments: number }>(response, 'Failed to fetch document stats')
}

export async function getCollectionOverviewStats(
  apiFetch: ApiFetch
): Promise<{ totalCollections: number; activeCollections: number }> {
  const response = await apiFetch('/api/v1/collections/stats/overview')
  return parseJsonOrThrow<{ totalCollections: number; activeCollections: number }>(
    response,
    'Failed to fetch collection stats'
  )
}

export async function getSearchStats(apiFetch: ApiFetch): Promise<SearchStats> {
  const response = await apiFetch('/api/v1/semantic-search/stats')
  return parseJsonOrThrow<SearchStats>(response, 'Failed to fetch search stats')
}

export async function getHealth(): Promise<{ status: string; service: string }> {
  const response = await fetch(`${getApiUrl()}/api/v1/health`)
  return parseJsonOrThrow<{ status: string; service: string }>(response, 'Failed to fetch health status')
}

export async function listUsers(apiFetch: ApiFetch): Promise<UserSessionRecord[]> {
  const response = await apiFetch('/api/v1/users')
  return parseJsonOrThrow<UserSessionRecord[]>(response, 'Failed to fetch users')
}
