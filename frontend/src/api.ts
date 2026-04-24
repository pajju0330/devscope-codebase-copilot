const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export interface Repo {
  id: string;
  name: string;
  url: string;
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';
  createdAt: string;
}

export interface CodeChunk {
  id: string;
  filePath: string;
  content: string;
  startLine: number;
  endLine: number;
  score: number;
}

export interface QueryResponse {
  explanation: string;
  relevantFiles: string[];
  chunks: CodeChunk[];
}

export const api = {
  async getRepos(): Promise<Repo[]> {
    const response = await fetch(`${API_BASE_URL}/repos`);
    if (!response.ok) throw new Error('Failed to fetch repositories');
    return response.json();
  },

  async ingestRepo(name: string, url: string): Promise<Repo> {
    const response = await fetch(`${API_BASE_URL}/repos/ingest/git`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, url }),
    });
    if (!response.ok) throw new Error('Failed to start ingestion');
    return response.json();
  },

  async getRepoStatus(repoId: string): Promise<Repo> {
    const response = await fetch(`${API_BASE_URL}/repos/${repoId}/status`);
    if (!response.ok) throw new Error('Failed to fetch repo status');
    return response.json();
  },

  async query(repoId: string, question: string): Promise<QueryResponse> {
    const response = await fetch(`${API_BASE_URL}/query`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ repoId, question }),
    });
    if (!response.ok) throw new Error('Failed to perform query');
    return response.json();
  },
};
