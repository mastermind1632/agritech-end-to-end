import { useEffect, useState } from 'react';
import StatCard from '../components/StatCard';
import { financeApi, groupApi, recommendationApi } from '../lib/api';

const money = (n) =>
  new Intl.NumberFormat('en-ZA', {
    style: 'currency',
    currency: 'ZAR',
    maximumFractionDigits: 0
  }).format(Number(n || 0));

export default function Dashboard({ farmer, setPage }) {
  const [summary, setSummary] = useState(null);
  const [groups, setGroups] = useState([]);
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let mounted = true;

    async function loadDashboard() {
      setLoading(true);
      setError('');

      try {
        const [finance, openGroups, recs] = await Promise.all([
          financeApi.summary(farmer.id),
          groupApi.list('open'),
          recommendationApi.list(farmer.id)
        ]);

        if (!mounted) return;

        setSummary(finance);
        setGroups(openGroups || []);
        setRecommendations(recs || []);
      } catch (err) {
        if (mounted) {
          setError(err.message || 'Unable to load dashboard data.');
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    loadDashboard();

    return () => {
      mounted = false;
    };
  }, [farmer.id]);

  const income = Number(summary?.totalIncome || 0);
  const expenses = Number(summary?.totalExpenses || 0);
  const netPosition = income - expenses;

  return (
    <>
      <div className="page-head">
        <div>
          <p className="eyebrow">YOUR FARM TODAY</p>

          <h1>
            Good day, {farmer.name?.split(' ')[0] || 'Farmer'}.
          </h1>

          <p className="muted">
            Here is how your farm is doing.
          </p>
        </div>

        <div className="head-actions">
          <button
            className="secondary"
            onClick={() => setPage('ai')}
          >
            See my buying tips
          </button>

          <button
            className="primary"
            onClick={() => setPage('finance')}
          >
            + Record transaction
          </button>
        </div>
      </div>

      {error && (
        <div className="notice error">
          <strong>Dashboard unavailable:</strong> {error}
        </div>
      )}

      <section className="stats">

        <StatCard
          label="Total income"
          value={loading ? 'Loading...' : money(income)}
          sub="Sales you added"
          icon="arrow"
          positive
        />

        <StatCard
          label="Total expenses"
          value={loading ? 'Loading...' : money(expenses)}
          sub="Costs you added"
          icon="wallet"
        />

        <StatCard
          label="Net position"
          value={loading ? 'Loading...' : money(netPosition)}
          sub="Income minus expenses"
          icon="leaf"
          positive={netPosition >= 0}
        />

        <StatCard
          label="Open group orders"
          value={loading ? '...' : groups.length}
          sub="Available opportunities"
          icon="users"
        />

      </section>

      <div className="dashboard-grid">

        {/* MAIN COMMAND PANEL */}
        <section className="panel hero-panel">

          <div className="panel-head">
            <div>
              <p className="eyebrow">FARM OVERVIEW</p>
              <h2>Your farm, in one view.</h2>
            </div>

            <span className="tag">
              {loading ? 'LOADING' : 'UP TO DATE'}
            </span>
          </div>

          <div className="insight-banner">

            <div className="insight-icon">
              ✦
            </div>

            <div>
              <b>Save money by buying together</b>

              <p>
                Explore open orders and compare listed prices.
                Purchase matches use your recorded activity;
                supplier terms still need confirmation.
              </p>
            </div>

            <button onClick={() => setPage('ai')}>
              See my tips →
            </button>

          </div>

          <div className="quick-grid">

            <button onClick={() => setPage('finance')}>
              <b>Track finances</b>
              <span>Add income or expenses</span>
            </button>

            <button onClick={() => setPage('market')}>
              <b>Compare suppliers</b>
              <span>Browse available products</span>
            </button>

            <button onClick={() => setPage('groups')}>
              <b>Join a group</b>
              <span>Unlock bulk discounts</span>
            </button>

          </div>

        </section>

        {/* AI RECOMMENDATIONS */}
        <section className="panel">

          <div className="panel-head">
            <div>
              <p className="eyebrow">JUST FOR YOU</p>
              <h2>Buying tips</h2>
            </div>

            <button
              className="text-button"
              onClick={() => setPage('ai')}
            >
              View all
            </button>
          </div>

          {loading && (
            <>
              <div className="list-row">
                <span className="round-icon">✦</span>
                <div>
                  <b>Looking at your spending...</b>
                  <p>
                    Finding products that could cost you less.
                  </p>
                </div>
              </div>

              <div className="list-row">
                <span className="round-icon">✦</span>
                <div>
                  <b>Getting your tips ready...</b>
                  <p>
                    This only takes a moment.
                  </p>
                </div>
              </div>
            </>
          )}

          {!loading &&
            recommendations.slice(0, 3).map((recommendation, index) => (
              <div
                className="list-row"
                key={recommendation.id || index}
              >
                <span className="round-icon">
                  ✦
                </span>

                <div>
                  <b>{recommendation.productName || 'Buying opportunity'}</b>

                  <p>
                    {recommendation.reason ||
                      'You could save by joining a group order for this product.'}
                  </p>
                </div>
              </div>
            ))}

          {!loading && !recommendations.length && (
            <div className="empty">

              <span>✦</span>

              <b>
                No buying tips yet
              </b>

              <p>
                No available group-buying opportunities right now.
              </p>

              <button
                className="secondary"
                onClick={() => setPage('ai')}
              >
                Open Buying Tips
              </button>

            </div>
          )}

        </section>

      </div>

      {/* GROUP PURCHASING */}
      <section className="panel table-panel">

        <div className="panel-head">

          <div>
            <p className="eyebrow">
              COLLECTIVE BUYING
            </p>

            <h2>
              Open group orders
            </h2>
          </div>

          <button
            className="text-button"
            onClick={() => setPage('groups')}
          >
            View groups →
          </button>

        </div>

        {loading ? (
          <div className="empty">
            <span>◌</span>
            <b>Loading group orders...</b>
          </div>
        ) : groups.length === 0 ? (
          <div className="empty">
            <span>👥</span>

            <b>
              No open group orders
            </b>

            <p>
              When a farmer starts a group order, it will show up here so you can join.
            </p>

            <button
              className="secondary"
              onClick={() => setPage('groups')}
            >
              Explore Group Orders
            </button>
          </div>
        ) : (
          <div className="table-wrap">

            <table>

              <thead>
                <tr>
                  <th>Product</th>
                  <th>Target quantity</th>
                  <th>Current quantity</th>
                  <th>Discount</th>
                  <th>Status</th>
                </tr>
              </thead>

              <tbody>

                {groups.slice(0, 5).map((group, index) => (

                  <tr key={group.id || index}>

                    <td>
                      <b>
                        {group.productName ||
                          recommendations.find(r => r.suggestedGroupOrderId === group.id)?.productName ||
                          group.product ||
                          'Group order'}
                      </b>
                    </td>

                    <td>
                      {group.targetQuantity ??
                        group.targetQty ??
                        '-'}
                    </td>

                    <td>
                      {group.currentQuantity ??
                        group.currentQty ??
                        '-'}
                    </td>

                    <td>
                      {group.discountRate != null
                        ? `${group.discountRate}%`
                        : '-'}
                    </td>

                    <td>
                      <span className="status success">
                        OPEN
                      </span>
                    </td>

                  </tr>

                ))}

              </tbody>

            </table>

          </div>
        )}

      </section>
    </>
  );
}
