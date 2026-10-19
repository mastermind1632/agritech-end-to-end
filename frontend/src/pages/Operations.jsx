import { useState, useEffect } from 'react';
import { complianceApi, exportApi, statementApi } from '../lib/api';

export default function Operations({ farmer }) {
  const [activeTab, setActiveTab] = useState('compliance');

  // Compliance state
  const [compliance, setCompliance] = useState(null);
  const [reminders, setReminders] = useState([]);
  const [compLoading, setCompLoading] = useState(false);

  // Export state
  const [shipments, setShipments] = useState([]);
  const [expLoading, setExpLoading] = useState(false);

  // Statement state
  const [statementResult, setStatementResult] = useState(null);
  const [stmtLoading, setStmtLoading] = useState(false);

  // Load data on mount
  useEffect(() => {
    if (activeTab === 'compliance') loadCompliance();
    if (activeTab === 'export') loadExport();
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

  async function loadExport() {
    setExpLoading(true);
    try {
      const data = await exportApi.getShipments(farmer.id);
      setShipments(data.shipments || []);
    } catch (err) {
      console.error(err);
    } finally {
      setExpLoading(false);
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

  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '2rem', marginBottom: '2rem' }}>Farm Operations</h1>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '1rem', borderBottom: '1px solid #eaeaea', marginBottom: '2rem' }}>
        {['compliance', 'export', 'statement'].map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            style={{ padding: '1rem', background: 'none', border: 'none', borderBottom: activeTab === tab ? '2px solid #2f5d50' : '2px solid transparent', fontWeight: activeTab === tab ? 'bold' : 'normal', cursor: 'pointer', textTransform: 'capitalize' }}>
            {tab}
          </button>
        ))}
      </div>

      {/* Compliance Tab */}
      {activeTab === 'compliance' && (
        <div>
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

      {/* Export Tab */}
      {activeTab === 'export' && (
        <div>
          <h2 style={{ marginBottom: '1rem' }}>PPECB Export Readiness</h2>
          {expLoading ? <p>Loading...</p> : (
            <>
              {shipments.length === 0 ? <p>No export shipments created yet.</p> : (
                <table style={{ width: '100%', borderCollapse: 'collapse', marginBottom: '2rem' }}>
                  <thead><tr style={{ background: '#eee', textAlign: 'left' }}><th style={{ padding: '0.5rem' }}>Product</th><th style={{ padding: '0.5rem' }}>Destination</th><th style={{ padding: '0.5rem' }}>Stage</th><th style={{ padding: '0.5rem' }}>Phytosanitary</th></tr></thead>
                  <tbody>
                    {shipments.map(s => (
                      <tr key={s.id} style={{ borderBottom: '1px solid #eee' }}>
                        <td style={{ padding: '0.5rem' }}>{s.productName}</td>
                        <td style={{ padding: '0.5rem' }}>{s.destinationCountry}</td>
                        <td style={{ padding: '0.5rem' }}>{s.exportStage}</td>
                        <td style={{ padding: '0.5rem' }}>{s.phytosanitaryStatus}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </div>
      )}

      {/* Statement Tab */}
      {activeTab === 'statement' && (
        <div>
          <h2 style={{ marginBottom: '1rem' }}>Expense Statement Analysis</h2>
          <p style={{ color: '#666', marginBottom: '1.5rem' }}>Simulate uploading a bank statement to check balance validity and categorise farm expenses.</p>

          <form onSubmit={handleAnalyzeStatement} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxWidth: '500px', marginBottom: '2rem' }}>
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
              {stmtLoading ? 'Analyzing...' : 'Analyze Statement'}
            </button>
          </form>

          {statementResult && (
            <div style={{ padding: '1.5rem', background: '#eef6f3', borderRadius: '8px' }}>
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
