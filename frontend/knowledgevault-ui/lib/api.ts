// Helper function to get API base URL
export function getApiUrl(): string {
  if (typeof window !== 'undefined') {
    return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
  }
  // Server-side: use backend service name
  return 'http://backend:8080'
}

// Helper function to get auth token
export function getAuthToken(): string | null {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('token')
  }
  return null
}