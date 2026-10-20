import { useState, useEffect } from 'react';
import { complianceApi, statementApi } from '../lib/api';

export default function Operations({ farmer, setPage }) {
  const [activeTab, setActiveTab] = useState('compliance');
  
  // Compliance state
  const [compliance, setCompliance] = useState(null);
  const [reminders, setReminders] = useState([]);
  const [compLoading, setCompLoading] = useState(false);
  
  // Statement state
  const [statementResult, setStatementResult] = useState(null);
  const [stmtLoading, setStmtLoading] = useState(false);
  const [uploadError, setUploadError] = useState(null);
  const [pdfPassword, setPdfPassword] = useState('');

  useEffect(() => {
    if (activeTab === 'compliance') loadCompliance();
  }, [activeTab]);

  async function loadCompliance() {
    setCompLoading(true);
    try {
      const data = await complianceApi.getChecklist(farmer.id);
      setCompliance(data);
      const rData = await complianceApi.getReminders(farmer.id);
      setReminders(rData || []);
    } catch (err) {
      console.error(err);
    } finally {
      setCompLoading(false);
    }
  }

  async function handleRegenerateReminders() {
    setCompLoading(true);
    try {
      await complianceApi.regenerateReminders(farmer.id);
      await loadCompliance();
    } catch (err) {
      alert(err.message);
    } finally {
      setCompLoading(false);
    }
  }

  async function handleAnalyzeStatement(e) {
    e.preventDefault();
    setStmtLoading(true);
    const form = e.target;
    const payload = {
      openingBalance: Number(form.opening.value),
      closingBalance: Number(form.closing.value),
      transactions: [
        { date: "2026-10-01", description: form.desc1.value, amount: Number(form.amt1.value), cropTag: "General" },
        { date: "2026-10-05", description: form.desc2.value, amount: Number(form.amt2.value), cropTag: "General" }
      ]
    };
    try {
      const res = await statementApi.analyze(payload);
      setStatementResult(res);
    } catch (err) {
      alert(err.message);
    } finally {
      setStmtLoading(false);
    }
  }

  async function handlePdfUpload(e) {
    e.preventDefault();
    setStmtLoading(true);
    setUploadError(null);
    const file = e.target.elements.pdf.files[0];
    if (!file) {
      setUploadError("Please choose a PDF file first.");
      setStmtLoading(false);
      return;
    }
    try {
      const res = await statementApi.uploadAndAnalyze(file, pdfPassword);
      setStatementResult(res);
    } catch (err) {
      setUploadError(err.message || "Upload failed");
    } finally {
      setStmtLoading(false);
    }
  }

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '2rem', marginBottom: '2rem' }}>Farm Operations</h1>
      
      <div style={{ display: 'flex', gap: '1rem', borderBottom: '1px solid #eaeaea', marginBottom: '2rem' }}>
        {['compliance', 'statement'].map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            style={{ padding: '1rem', background: 'none', border: 'none', borderBottom: activeTab === tab ? '2px solid #2f5d50' : '2px solid transparent', fontWeight: activeTab === tab ? 'bold' : 'normal', cursor: 'pointer', textTransform: 'capitalize' }}>
            {tab}
          </button>
        ))}
      </div>

      {activeTab === 'compliance' && (
        <div>
          <div style={{ padding: '1rem 1.5rem', background: '#eef6f3', borderRadius: '8px', marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <strong>Need to export your produce?</strong>
              <div style={{ color: '#555', fontSize: '0.9em' }}>Manage PPECB, Department of Agriculture, and SARS Customs workflows.</div>
            </div>
            <button onClick={() => setPage && setPage('exports')} style={{ padding: '0.6rem 1.2rem', background: '#2f5d50', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 }}>
              Go to Export Workflow ?
            </button>
          </div>

          <h2 style={{ marginBottom: '1rem' }}>Business Compliance & Reminders</h2>
          {compLoading ? <p>Loading...</p> : compliance ? (
            <>
              <div style={{ marginBottom: '2rem', padding: '1.5rem', background: '#f9f9f9', borderRadius: '8px' }}>
                <h3 style={{ marginTop: 0 }}>Checklist (Score: {compliance.progressScore}%)</h3>
                <ul style={{ listStyle: 'none', padding: 0 }}>
                  {compliance.checklist.map((item, i) => (
                    <li key={i} style={{ padding: '0.5rem 0', borderBottom: '1px solid #eee' }}>
                      <span style={{ color: item.completed ? 'green' : 'gray', marginRight: '0.5rem' }}>{item.completed ? '?' : '?'}</span>
                      <strong>{item.stage}</strong> <span style={{ color: '#666', fontSize: '0.9em' }}>({item.authority})</span>
                    </li>
                  ))}
                </ul>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h3 style={{ margin: 0 }}>Upcoming Deadlines</h3>
                <button onClick={handleRegenerateReminders} style={{ padding: '0.5rem 1rem', cursor: 'pointer' }}>Refresh Schedule</button>
              </div>
              {reminders.length === 0 ? <p>No upcoming reminders.</p> : (
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead><tr style={{ background: '#eee', textAlign: 'left' }}><th style={{ padding: '0.5rem' }}>Type</th><th style={{ padding: '0.5rem' }}>Due Date</th><th style={{ padding: '0.5rem' }}>Days Before</th><th style={{ padding: '0.5rem' }}>Status</th></tr></thead>
                  <tbody>
                    {reminders.map(r => (
                      <tr key={r.id} style={{ borderBottom: '1px solid #eee' }}>
                        <td style={{ padding: '0.5rem' }}>{r.reminderType}</td>
                        <td style={{ padding: '0.5rem' }}>{r.dueDate}</td>
                        <td style={{ padding: '0.5rem' }}>{r.daysBefore}</td>
                        <td style={{ padding: '0.5rem' }}>{r.sent ? '? Sent' : '? Pending'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          ) : <p>Could not load compliance data.</p>}
        </div>
      )}

      {activeTab === 'statement' && (
        <div>
          <h2 style={{ marginBottom: '1rem' }}>Expense Statement Analysis</h2>
          
          <div style={{ padding: '1.5rem', background: '#f9f9f9', borderRadius: '8px', marginBottom: '2rem' }}>
            <h3 style={{ marginTop: 0 }}>Option 1: Upload a PDF bank statement</h3>
            <p style={{ color: '#666', marginBottom: '1rem' }}>We'll extract transactions, validate the opening/closing balance, and categorise every line.</p>
            <form onSubmit={handlePdfUpload} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '500px' }}>
              <input name="pdf" type="file" accept="application/pdf" style={{ width: '100%' }} />
              <label>
                PDF password (if protected)
                <input
                  type="password"
                  value={pdfPassword}
                  onChange={e => setPdfPassword(e.target.value)}
                  placeholder="Usually your ID number or account number"
                  style={{ width: '100%', padding: '0.5rem', marginTop: '0.25rem' }}
                />
              </label>
              <button type="submit" disabled={stmtLoading} style={{ padding: '0.75rem 1.5rem', background: '#2f5d50', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', alignSelf: 'flex-start' }}>
                {stmtLoading ? 'Processing...' : 'Upload & Analyse'}
              </button>
            </form>
            {uploadError && <div style={{ color: '#c33', marginTop: '0.5rem' }}>{uploadError}</div>}
          </div>

          <div style={{ padding: '1.5rem', background: '#f9f9f9', borderRadius: '8px' }}>
            <h3 style={{ marginTop: 0 }}>Option 2: Enter transactions manually</h3>
            <form onSubmit={handleAnalyzeStatement} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '500px' }}>
              <label>Opening Balance: <input name="opening" type="number" defaultValue="10000" style={{ width: '100%', padding: '0.5rem' }} /></label>
              <label>Closing Balance: <input name="closing" type="number" defaultValue="8500" style={{ width: '100%', padding: '0.5rem' }} /></label>
              <div style={{ display: 'flex', gap: '1rem' }}>
                <label style={{ flex: 1 }}>Txn 1 Desc: <input name="desc1" defaultValue="Engen Diesel" style={{ width: '100%', padding: '0.5rem' }} /></label>
                <label style={{ flex: 1 }}>Amount: <input name="amt1" type="number" defaultValue="-1200" style={{ width: '100%', padding: '0.5rem' }} /></label>
              </div>
              <div style={{ display: 'flex', gap: '1rem' }}>
                <label style={{ flex: 1 }}>Txn 2 Desc: <input name="desc2" defaultValue="Omnia Fertiliser" style={{ width: '100%', padding: '0.5rem' }} /></label>
                <label style={{ flex: 1 }}>Amount: <input name="amt2" type="number" defaultValue="-800" style={{ width: '100%', padding: '0.5rem' }} /></label>
              </div>
              <button type="submit" disabled={stmtLoading} style={{ padding: '0.75rem', background: '#2f5d50', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
                {stmtLoading ? 'Analyzing...' : 'Analyze Manually'}
              </button>
            </form>
          </div>

          {statementResult && (
            <div style={{ marginTop: '2rem', padding: '1.5rem', background: '#eef6f3', borderRadius: '8px' }}>
              <h3 style={{ marginTop: 0 }}>Analysis Result: {statementResult.status}</h3>
              <p><strong>Balance Valid:</strong> {statementResult.balanceValid ? '? Yes' : '? No'}</p>
              <p><strong>Expected Closing:</strong> R {statementResult.expectedClosingBalance}</p>
              <h4>Categorised Totals:</h4>
              <ul>
                {Object.entries(statementResult.categoryTotals).map(([cat, amt]) => (
                  <li key={cat}>{cat}: R {amt}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}


