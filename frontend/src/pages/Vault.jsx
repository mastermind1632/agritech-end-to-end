import { useEffect, useRef, useState } from 'react';
import { vaultApi, consentApi } from '../lib/api';

const DOC_TYPES = [
  { value: 'ID_COPY', label: 'ID / Passport copy' },
  { value: 'CIPC', label: 'CIPC registration' },
  { value: 'SARS_TAX', label: 'SARS tax certificate' },
  { value: 'VAT_CERT', label: 'VAT registration' },
  { value: 'PERMIT', label: 'Permit / licence' },
  { value: 'CERTIFICATE', label: 'Certificate (GLOBALG.A.P. etc.)' },
  { value: 'EXPORT_CERT', label: 'Export certificate' },
  { value: 'OTHER', label: 'Other' }
];

export default function Vault({ farmer }) {
  const [docs, setDocs] = useState([]);
  const [consent, setConsent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [docType, setDocType] = useState('ID_COPY');
  const [notes, setNotes] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const fileInput = useRef(null);

  async function refresh() {
    setLoading(true);
    setError('');
    try {
      const c = await consentApi.get(farmer.id);
      setConsent(c);
      if (c.DATA_PROCESSING) {
        const list = await vaultApi.list(farmer.id);
        setDocs(list.value || list || []);
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refresh(); }, [farmer.id]);

  async function grantConsent() {
    setBusy(true); setError('');
    try {
      await consentApi.record(farmer.id, 'DATA_PROCESSING', true);
      setInfo('Consent recorded. You can now upload documents.');
      await refresh();
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }

  async function handleUpload(e) {
    e.preventDefault();
    const file = fileInput.current?.files?.[0];
    if (!file) { setError('Choose a file first'); return; }
    setBusy(true); setError(''); setInfo('');
    try {
      await vaultApi.upload(file, docType, notes || null, expiresAt || null);
      setInfo(`Uploaded: ${file.name}`);
      if (fileInput.current) fileInput.current.value = '';
      setNotes(''); setExpiresAt('');
      await refresh();
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }

  async function handleDownload(doc) {
    setError('');
    try {
      const blob = await vaultApi.download(doc.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.originalFilename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) { setError(e.message); }
  }

  async function handleDelete(doc) {
    if (!confirm(`Delete "${doc.originalFilename}"? This cannot be undone.`)) return;
    setBusy(true); setError('');
    try {
      await vaultApi.delete(doc.id);
      await refresh();
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }

  if (loading) return <div style={{ padding: '2rem' }}>Loading vault?</div>;

  if (!consent?.DATA_PROCESSING) {
    return (
      <div style={{ padding: '2rem', maxWidth: '700px', margin: '0 auto' }}>
        <h1>Document Vault</h1>
        <div style={{ padding: '1.5rem', background: '#fff8e1', border: '1px solid #f0c000', borderRadius: '8px' }}>
          <h3 style={{ marginTop: 0 }}>POPIA consent required</h3>
          <p>To store IDs, certificates, and permits securely, we need your explicit consent to process this personal information (POPIA, Section 11).</p>
          <p>Your documents will be <strong>encrypted at rest</strong> (AES-256) and only accessible to you.</p>
          <button onClick={grantConsent} disabled={busy} style={{ padding: '0.75rem 1.5rem', background: '#2f5d50', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
            {busy ? 'Recording?' : 'I consent to data processing'}
          </button>
          {error && <div style={{ color: '#c33', marginTop: '0.5rem' }}>{error}</div>}
        </div>
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem', maxWidth: '1000px', margin: '0 auto' }}>
      <h1>Document Vault</h1>
      <p style={{ color: '#666' }}>Encrypted storage for your IDs, certificates, and permits. Files are AES-256 encrypted at rest.</p>

      {info && <div style={{ padding: '0.75rem', background: '#eef6f3', border: '1px solid #2f5d50', borderRadius: '4px', marginBottom: '1rem' }}>{info}</div>}
      {error && <div style={{ padding: '0.75rem', background: '#fde8e8', border: '1px solid #c33', borderRadius: '4px', marginBottom: '1rem', color: '#c33' }}>{error}</div>}

      <div style={{ padding: '1.5rem', background: '#f9f9f9', borderRadius: '8px', marginBottom: '2rem' }}>
        <h3 style={{ marginTop: 0 }}>Upload a document</h3>
        <form onSubmit={handleUpload} style={{ display: 'grid', gap: '1rem', maxWidth: '500px' }}>
          <label>Document type
            <select value={docType} onChange={e => setDocType(e.target.value)} style={{ width: '100%', padding: '0.5rem', marginTop: '0.25rem' }}>
              {DOC_TYPES.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}
            </select>
          </label>
          <label>File (max 20MB)
            <input ref={fileInput} type="file" style={{ width: '100%', marginTop: '0.25rem' }} />
          </label>
          <label>Expiry date (optional)
            <input type="date" value={expiresAt} onChange={e => setExpiresAt(e.target.value)} style={{ width: '100%', padding: '0.5rem', marginTop: '0.25rem' }} />
          </label>
          <label>Notes (optional)
            <input type="text" value={notes} onChange={e => setNotes(e.target.value)} style={{ width: '100%', padding: '0.5rem', marginTop: '0.25rem' }} />
          </label>
          <button type="submit" disabled={busy} style={{ padding: '0.75rem', background: '#2f5d50', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
            {busy ? 'Uploading?' : 'Upload'}
          </button>
        </form>
      </div>

      <h3>Your documents ({docs.length})</h3>
      {docs.length === 0 && <p>No documents uploaded yet.</p>}
      {docs.length > 0 && (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr style={{ background: '#eee', textAlign: 'left' }}>
            <th style={{ padding: '0.5rem' }}>Type</th>
            <th style={{ padding: '0.5rem' }}>Filename</th>
            <th style={{ padding: '0.5rem' }}>Size</th>
            <th style={{ padding: '0.5rem' }}>Expires</th>
            <th style={{ padding: '0.5rem' }}>Actions</th>
          </tr></thead>
          <tbody>
            {docs.map(doc => (
              <tr key={doc.id} style={{ borderBottom: '1px solid #eee' }}>
                <td style={{ padding: '0.5rem' }}>{doc.docType}</td>
                <td style={{ padding: '0.5rem' }}>{doc.originalFilename}</td>
                <td style={{ padding: '0.5rem' }}>{(doc.fileSize / 1024).toFixed(1)} KB</td>
                <td style={{ padding: '0.5rem' }}>{doc.expiresAt || '?'}</td>
                <td style={{ padding: '0.5rem' }}>
                  <button onClick={() => handleDownload(doc)} style={{ marginRight: '0.5rem', padding: '0.25rem 0.75rem', cursor: 'pointer' }}>Download</button>
                  <button onClick={() => handleDelete(doc)} disabled={busy} style={{ padding: '0.25rem 0.75rem', cursor: 'pointer', color: '#c33' }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
