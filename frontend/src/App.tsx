import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Link, useParams } from 'react-router-dom';
import { Plus, Search, Code, Book, ExternalLink, Layout as LayoutIcon, ChevronRight, Activity, Terminal, Folder, Calendar } from 'lucide-react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { api } from './api';
import type { Repo, QueryResponse, CodeChunk } from './api';
import './App.css';

function Layout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <header>
        <div className="container">
          <Link to="/" className="logo">
            <LayoutIcon size={24} />
            DevScope
          </Link>
          <nav>
            <a href="https://github.com/pajju0330/devscope-codebase-copilot" target="_blank" className="btn" rel="noreferrer">
              <ExternalLink size={18} />
              GitHub
            </a>
          </nav>
        </div>
      </header>
      <main className="container" style={{ padding: '2rem 0' }}>
        {children}
      </main>
    </>
  );
}

function CollapsibleChunk({ chunk }: { chunk: CodeChunk }) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="card" style={{ padding: 0, overflow: 'hidden', marginBottom: '1rem' }}>
      <button 
        onClick={() => setIsOpen(!isOpen)}
        style={{ 
          width: '100%', 
          background: 'rgba(255,255,255,0.05)', 
          border: 'none', 
          borderBottom: isOpen ? '1px solid var(--border)' : 'none',
          padding: '0.75rem 1rem', 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center',
          color: 'inherit',
          textAlign: 'left',
          cursor: 'pointer'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', overflow: 'hidden' }}>
          <ChevronRight 
            size={18} 
            style={{ 
              transform: isOpen ? 'rotate(90deg)' : 'none', 
              transition: 'transform 0.2s',
              color: 'var(--accent)',
              flexShrink: 0
            }} 
          />
          <span style={{ fontFamily: 'monospace', fontSize: '0.9rem', color: 'var(--accent)', wordBreak: 'break-all' }}>
            {chunk.filePath}
          </span>
        </div>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)', whiteSpace: 'nowrap', marginLeft: '1rem' }}>
          Lines {chunk.startLine}-{chunk.endLine}
        </span>
      </button>
      
      {isOpen && (
        <div style={{ animation: 'fadeIn 0.2s ease-out' }}>
          <SyntaxHighlighter
            language="java"
            style={vscDarkPlus}
            customStyle={{ margin: 0, padding: '1rem', fontSize: '0.85rem' }}
            showLineNumbers
            startingLineNumber={chunk.startLine}
          >
            {chunk.content}
          </SyntaxHighlighter>
        </div>
      )}
    </div>
  );
}

function Dashboard() {
  const [repos, setRepos] = useState<Repo[]>([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newRepo, setNewRepo] = useState({ repoName: '', repoUrl: '' });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRepos();
  }, []);

  const loadRepos = async () => {
    try {
      const data = await api.getRepos();
      setRepos(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleAddRepo = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.ingestRepo(newRepo.repoName, newRepo.repoUrl);
      setIsModalOpen(false);
      setNewRepo({ repoName: '', repoUrl: '' });
      loadRepos();
    } catch (err) {
      alert('Failed to add repository');
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1>Your Repositories</h1>
        <button className="btn btn-primary" onClick={() => setIsModalOpen(true)}>
          <Plus size={18} />
          Add Repository
        </button>
      </div>

      {loading ? (
        <p>Loading repositories...</p>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1.5rem' }}>
          {repos.map(repo => (
            <Link key={repo.repoId} to={`/repo/${repo.repoId}`} className="card" style={{ textDecoration: 'none', color: 'inherit', display: 'flex', flexDirection: 'column' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.25rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <div style={{ padding: '0.5rem', background: 'rgba(88, 166, 255, 0.1)', borderRadius: '8px', color: 'var(--accent)' }}>
                    <Folder size={20} />
                  </div>
                  <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 600 }}>{repo.repoName}</h3>
                </div>
                <span className={`status-badge status-${repo.status.toLowerCase()}`}>
                  {repo.status}
                </span>
              </div>
              
              <p style={{ color: 'var(--text-dim)', fontSize: '0.85rem', marginBottom: '1.5rem', wordBreak: 'break-all', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', flexGrow: 1 }}>
                {repo.repoUrl}
              </p>
              
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--border)', paddingTop: '1rem', marginTop: 'auto' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: 'var(--text-dim)', fontSize: '0.75rem' }}>
                  <Calendar size={14} />
                  <span>{new Date(repo.createdAt).toLocaleDateString()}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', color: 'var(--accent)', fontSize: '0.85rem', fontWeight: '600', gap: '0.25rem' }}>
                  Query <ChevronRight size={16} />
                </div>
              </div>
            </Link>
          ))}
          {repos.length === 0 && (
            <div className="card" style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '3rem' }}>
              <Activity size={48} style={{ marginBottom: '1rem', color: 'var(--text-dim)' }} />
              <h3>No repositories indexed yet</h3>
              <p style={{ color: 'var(--text-dim)' }}>Add your first repository to start querying with AI.</p>
            </div>
          )}
        </div>
      )}

      {isModalOpen && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div className="card" style={{ width: '100%', maxWidth: '500px' }}>
            <h2>Add New Repository</h2>
            <form onSubmit={handleAddRepo} style={{ marginTop: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '0.5rem' }}>Repository Name</label>
                <input 
                  type="text" 
                  placeholder="e.g. My Awesome App" 
                  required 
                  value={newRepo.repoName}
                  onChange={e => setNewRepo({ ...newRepo, repoName: e.target.value })}
                />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '0.5rem' }}>Git URL</label>
                <input 
                  type="url" 
                  placeholder="https://github.com/user/repo" 
                  required 
                  value={newRepo.repoUrl}
                  onChange={e => setNewRepo({ ...newRepo, repoUrl: e.target.value })}
                />
              </div>
              <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>Add Repository</button>
                <button type="button" className="btn" style={{ flex: 1 }} onClick={() => setIsModalOpen(false)}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function RepoQueryView() {
  const { repoId } = useParams();
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<QueryResponse | null>(null);
  const [repo, setRepo] = useState<Repo | null>(null);

  useEffect(() => {
    if (repoId) {
      api.getRepoStatus(repoId).then(setRepo).catch(console.error);
    }
  }, [repoId]);

  const handleQuery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!repoId || !question.trim()) return;

    setLoading(true);
    try {
      const data = await api.query(repoId, question);
      setResult(data);
    } catch (err) {
      alert('Failed to get answer');
    } finally {
      setLoading(false);
    }
  };

  if (!repo) return <div>Loading repository info...</div>;

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 350px', gap: '2rem' }}>
      <div>
        <div style={{ marginBottom: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.5rem' }}>
            <Link to="/" style={{ color: 'var(--text-dim)' }}>Repositories</Link>
            <ChevronRight size={16} style={{ color: 'var(--text-dim)' }} />
            <h1 style={{ margin: 0 }}>{repo.repoName}</h1>
          </div>
          <p style={{ color: 'var(--text-dim)' }}>{repo.repoUrl}</p>
        </div>

        <form onSubmit={handleQuery} style={{ position: 'relative', marginBottom: '2rem' }}>
          <textarea
            rows={3}
            placeholder="Ask anything about the codebase... (e.g. 'How does authentication work?')"
            value={question}
            onChange={e => setQuestion(e.target.value)}
            style={{ paddingRight: '120px', resize: 'vertical' }}
          />
          <button 
            type="submit" 
            className="btn btn-primary" 
            disabled={loading}
            style={{ position: 'absolute', bottom: '12px', right: '12px' }}
          >
            {loading ? <Activity className="spin" size={18} /> : <Search size={18} />}
            Ask AI
          </button>
        </form>

        {result && (
          <div className="explanation-view" style={{ animation: 'fadeIn 0.5s ease-in' }}>
            <div className="card" style={{ marginBottom: '2rem', borderLeft: '4px solid var(--accent)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', color: 'var(--accent)' }}>
                <Terminal size={20} />
                <h2 style={{ fontSize: '1.25rem', margin: 0 }}>Explanation</h2>
              </div>
              <p style={{ whiteSpace: 'pre-wrap', lineHeight: '1.6' }}>{result.explanation}</p>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
              <h2 style={{ fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Code size={20} />
                Code References
              </h2>
              {result.chunks.map((chunk, idx) => (
                <CollapsibleChunk key={idx} chunk={chunk} />
              ))}
            </div>
          </div>
        )}
      </div>

      <aside>
        <div className="card">
          <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', fontSize: '1rem' }}>
            <Book size={18} />
            Relevant Files
          </h3>
          {result ? (
            <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              {result.relevantFiles.map((file, idx) => {
                const fileName = file.split('/').pop();
                return (
                  <li key={idx} title={file} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.9rem', color: 'var(--text-dim)', overflow: 'hidden' }}>
                    <Code size={14} style={{ flexShrink: 0 }} />
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {fileName}
                    </span>
                  </li>
                );
              })}
            </ul>
          ) : (
            <p style={{ fontSize: '0.9rem', color: 'var(--text-dim)', fontStyle: 'italic' }}>
              Submit a query to see relevant files.
            </p>
          )}
        </div>
        
        <div className="card" style={{ marginTop: '1rem' }}>
          <h3 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Indexing Stats</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-dim)' }}>
            Status: <span className={`status-badge status-${repo.status.toLowerCase()}`}>{repo.status}</span>
          </p>
        </div>
      </aside>
    </div>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/repo/:repoId" element={<RepoQueryView />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  );
}

export default App;
