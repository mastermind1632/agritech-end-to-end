import Icon from './Icon';
export default function StatCard({label,value,sub,icon,positive}){return <div className="stat-card"><div className="stat-top"><span>{label}</span><span className="stat-icon"><Icon name={icon}/></span></div><strong>{value}</strong><small className={positive?'positive':''}>{sub}</small></div>}
