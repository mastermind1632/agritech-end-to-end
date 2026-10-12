export default function DeploymentNotice() {
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname);
  if (window.location.protocol === 'https:' || local) return null;
  return <div className="deployment-notice" role="status">Live test environment: connection is not encrypted. Use synthetic data, not real farm locations or bank information.</div>;
}
