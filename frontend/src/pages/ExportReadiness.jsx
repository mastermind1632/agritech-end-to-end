import { useEffect, useMemo, useState } from 'react';
import { exportApi } from '../lib/api';

const BLANK_APPLICATION = {
  product: '',
  variety: '',
  destinationCountry: '',
  productionLocation: '',
  province: '',
  estimatedQuantity: '',
  quantityUnit: 'kg'
};

const STAGES = [
  {
    name: 'Eligibility',
    meaning: 'Describe the product and the people involved, and work out which registrations may apply.',
    actions: 'Record product, variety, quantity, farm location, destination and buyer. Ask a customs source for tariff-classification guidance. Business, tax and exporter registrations may depend on your situation; do not assume every one applies.',
    how: 'Use the application details above for your farm and product. Ask the relevant authority or adviser which business, tax and exporter registrations apply, and note the answer in the task below. Keep the source and date of any tariff-classification advice.',
    documentGroups: {
      required: [],
      conditional: ['Business, tax or exporter registration evidence only if the relevant authority or adviser confirms it applies.'],
      buyerOptional: ['Buyer enquiry or agreement, if available.'],
      toConfirm: ['Any evidence SARS or a customs professional recommends for tariff-classification guidance.']
    },
    responsibility: 'You provide accurate farm and buyer details. SARS Customs can advise on exporter and tariff-classification processes. A business adviser can help assess business or tax registration questions.',
    sources: [
      ['SARS Customs', 'https://www.sars.gov.za/customs-and-excise/'],
      ['SARS exporters', 'https://www.sars.gov.za/customs-and-excise/registration-licensing-and-accreditation/exporters/']
    ],
    next: 'Use the exact product, origin and destination to ask whether market access exists.'
  },
  {
    name: 'Market access',
    meaning: 'Check whether this product from this origin can enter the destination market, and which conditions apply.',
    actions: 'Ask the South African plant-health authority and the destination authority about current market access, import permits, plant-health protocols, treatments and pesticide residue limits. Save the date, source and answer, including any open questions.',
    how: 'Search the official sources for the exact product and origin, then ask the responsible authorities to confirm any unclear result in writing. Record unanswered questions as action items; do not infer access from a general trade page.',
    documentGroups: {
      required: [],
      conditional: ['Import permits, plant-health certificates, inspection or treatment evidence only if current authorities require them.'],
      buyerOptional: ['Buyer residue-limit or quality specifications, if requested.'],
      toConfirm: ['Whether market access exists and which exact import or plant-health documents, limits or protocols apply.']
    },
    responsibility: 'The responsible South African plant-health authority and destination-country authorities confirm legal conditions. Buyers may add separate commercial specifications.',
    sources: [
      ['South African plant-health information (IPPC)', 'https://www.ippc.int/en/countries/south-africa/'],
      ['EU Access2Markets', 'https://trade.ec.europa.eu/access-to-markets/'],
      ['South African eCertification guidance (gov.za)', 'https://www.gov.za/services/services-organisations/export-permits/ecertification-ecert']
    ],
    next: 'Record only the registrations that the relevant authority or adviser confirms apply.'
  },
  {
    name: 'Registrations',
    meaning: 'Identify any exporter, farm, orchard, packhouse, customs or other registration that applies to this shipment.',
    actions: 'Ask the responsible authority what must be registered for your product and route. Record whether each registration is not assessed, applicable, not applicable, in progress or submitted. A submission is not an approval.',
    how: 'Contact each relevant authority with the product, destination and production location. Save application references and responses. Change a status to submitted only after you have sent the application.',
    documentGroups: {
      required: [],
      conditional: ['Exporter, farm, orchard or packhouse registration evidence only after the responsible authority confirms it applies.'],
      buyerOptional: ['Buyer or private-standard certification only if the buyer requests it.'],
      toConfirm: ['Which registrations apply and what application or supporting evidence each authority requires.']
    },
    responsibility: 'SARS handles its exporter and customs registration processes. The Department of Agriculture or responsible plant-health authority advises on applicable production and plant-health registrations. Other authorities may have separate roles.',
    sources: [
      ['SARS exporters', 'https://www.sars.gov.za/customs-and-excise/registration-licensing-and-accreditation/exporters/'],
      ['South African eCertification guidance (gov.za)', 'https://www.gov.za/services/services-organisations/export-permits/ecertification-ecert']
    ],
    next: 'Organise production records based on the requirements confirmed for this product and market.'
  },
  {
    name: 'Production compliance',
    meaning: 'Keep clear farm records so you can answer authority, buyer or inspection questions.',
    actions: 'Organise applicable spray and agricultural input records, pest monitoring, harvest dates, lot details and traceability. Check which certification or private buyer standards are requested; they are not automatically legal requirements.',
    how: 'Keep records by date, production area and lot where those details are used on your farm. Ask the buyer and responsible authority which records they need for this market before changing farm practice.',
    documentGroups: {
      required: [],
      conditional: ['Farm input, spray, pest-monitoring, harvest and traceability records that confirmed official controls require.'],
      buyerOptional: ['Private certification, audit or farm-practice evidence if requested by the buyer.'],
      toConfirm: ['Which official production records, residue controls and handling processes apply to this product and route.']
    },
    responsibility: 'You maintain farm records. The responsible plant-health authority can explain official controls. Buyers and private standards organisations set separate commercial requirements.',
    sources: [
      ['South African eCertification guidance (gov.za)', 'https://www.gov.za/services/services-organisations/export-permits/ecertification-ecert'],
      ['South African plant-health information (IPPC)', 'https://www.ippc.int/en/countries/south-africa/']
    ],
    next: 'Use confirmed product and buyer conditions to plan packing, transport and temperature handling.'
  },
  {
    name: 'Shipment preparation',
    meaning: 'Plan how the actual goods will be packed, identified and moved.',
    actions: 'Confirm packages, labels, quantity, freight, transport, cold-chain plan and delivery terms with the buyer and service providers. Ask whether inspection booking is needed. Record consignment references when known.',
    how: 'Agree the packing and delivery plan with the buyer, packhouse and freight provider. Ask PPECB or the relevant assignee whether an inspection or cold-chain service applies before you book.',
    documentGroups: {
      required: [],
      conditional: ['Packing, invoice, transport, consignment or handling documents required by the confirmed route, contract or service provider.'],
      buyerOptional: ['Buyer packing, labelling, pallet or temperature specifications, if requested.'],
      toConfirm: ['Which shipment documents are needed and whether inspection or cold-chain services apply.']
    },
    responsibility: 'You, your buyer, packhouse and freight provider plan the shipment. PPECB supports inspection, certification and cold-chain processes for applicable perishable exports; it does not handle every export requirement.',
    sources: [
      ['PPECB', 'https://ppecb.com/'],
      ['PPECB contact', 'https://ppecb.com/contact/']
    ],
    next: 'Confirm who will inspect or certify the goods before booking or presenting them.'
  },
  {
    name: 'Inspection and official certification',
    meaning: 'Arrange the applicable inspection and record results and certificates that are actually issued.',
    actions: 'Ask the responsible authority or designated assignee which inspection and certification process applies, who must book it and what to present. Record the actual date, place and result. Upload only certificates you have received.',
    how: 'Request current instructions before sending goods for inspection. Record booking details first, then enter the result after the inspection. Keep the original certificate; do not alter it in this app.',
    documentGroups: {
      required: [],
      conditional: ['Inspection booking and result, and an official certificate or addendum only if the competent authority confirms it applies and actually issues it.'],
      buyerOptional: ['Separate buyer-requested quality or inspection evidence.'],
      toConfirm: ['The current inspection and certification process, responsible authority or assignee, and documents to present.']
    },
    responsibility: 'The competent authority, its designated assignees and authorised certifying officers control official certification. PPECB performs relevant inspection, certification and cold-chain functions where applicable. AgriTech cannot inspect, approve or issue a certificate.',
    sources: [
      ['PPECB', 'https://ppecb.com/'],
      ['PPECB contact', 'https://ppecb.com/contact/'],
      ['South African eCertification guidance (gov.za)', 'https://www.gov.za/services/services-organisations/export-permits/ecertification-ecert']
    ],
    next: 'The supplied reference SOP is titled “Standard Operating Procedure on Official Export Certification of Regulated Agricultural Products in terms of the Agricultural Product Standards Act, 1990 (Act No. 119 of 1990)” and states an effective date of September 2020. Treat it as a reference only and confirm the current procedure with the authority or designated assignee.',
    sop: true
  },
  {
    name: 'Customs clearance',
    meaning: 'Confirm and complete the customs steps that apply to your export.',
    actions: 'Ask SARS or a customs professional which export declaration, invoice, packing list, transport document or proof of origin may apply to your product and transaction. Record evidence only when received.',
    how: 'Use current SARS guidance and confirm which documents apply to this route. South African export customs clearance and destination-country import clearance are separate processes.',
    documentGroups: {
      required: [],
      conditional: ['Export declarations, invoices, packing lists, transport documents and proof of origin may apply depending on the product, destination and transaction.'],
      buyerOptional: ['Additional commercial paperwork requested by the buyer or contract.'],
      toConfirm: ['The confirmed tariff classification, declaration support and any proof-of-origin or clearance evidence required for this shipment.']
    },
    responsibility: 'SARS Customs is responsible for South African customs processes. A customs broker may assist. Destination-country customs separately controls import clearance.',
    sources: [
      ['SARS Customs', 'https://www.sars.gov.za/customs-and-excise/'],
      ['SARS exporters', 'https://www.sars.gov.za/customs-and-excise/registration-licensing-and-accreditation/exporters/'],
      ['EU Access2Markets', 'https://trade.ec.europa.eu/access-to-markets/']
    ],
    next: 'After the real shipment outcome is known, record the outcome, evidence and any outstanding issues.'
  },
  {
    name: 'Shipped and closed',
    meaning: 'Keep an administrative record of what actually happened to the shipment.',
    actions: 'Enter the final consignment reference, actual outcome and date. Attach relevant evidence, note unresolved issues and record lessons.',
    how: 'Wait until you know the actual result, then choose the closest outcome and record its date. For Shipped or Partly shipped, record an applicable customs-related document under Stage 7. This tracker does not verify the document or confirm customs clearance.',
    documentGroups: {
      required: [],
      conditional: ['For a shipped or partly shipped outcome, this tracker requires a customs-related document to be recorded under Stage 7 before closing. This checks only that a file is recorded under that category; it does not verify the contents, authenticity or official acceptance of the document, or confirm customs clearance.'],
      buyerOptional: ['Buyer receipt or other outcome evidence, if available or requested.'],
      toConfirm: ['Which final transport, consignment and customs records apply to the actual shipment outcome.']
    },
    responsibility: 'You record the actual outcome and retain supporting records. SARS and destination authorities make their own customs decisions; AgriTech does not verify them.',
    sources: [
      ['SARS Customs', 'https://www.sars.gov.za/customs-and-excise/'],
      ['PPECB', 'https://ppecb.com/']
    ],
    next: 'Keep the record for your own follow-up and discuss unresolved regulatory questions with the responsible authority.'
  }
];

const TASK_STATUSES = [
  ['NOT_STARTED', 'Not started'],
  ['IN_PROGRESS', 'In progress'],
  ['AWAITING_DOCUMENTS', 'Awaiting documents'],
  ['SUBMITTED', 'Submitted / awaiting official review'],
  ['EVIDENCE_CHECKED', 'Evidence checked for internal tracking'],
  ['ACTION_REQUIRED', 'Action required'],
  ['COMPLETED', 'Completed'],
  ['NOT_APPLICABLE', 'Not applicable']
];

const DOC_TYPES = [
  ['BUSINESS_REGISTRATION', 'Business or registration evidence'],
  ['MARKET_ACCESS', 'Market-access evidence'],
  ['PRODUCTION_RECORD', 'Production records'],
  ['CONSIGNMENT_NOTE', 'Consignment note'],
  ['INSPECTION_RECORD', 'Inspection record'],
  ['OFFICIAL_CERTIFICATE', 'Official certificate actually received'],
  ['COMMERCIAL_DOCUMENT', 'Commercial document'],
  ['CUSTOMS_CLEARANCE', 'Customs-related document (recorded by you)'],
  ['OTHER', 'Other']
];

const DOC_STATUSES = [
  ['UPLOADED', 'Uploaded'],
  ['AWAITING_INTERNAL_REVIEW', 'Awaiting internal review'],
  ['CHECKED_FOR_INTERNAL_TRACKING', 'Checked for internal tracking']
];

const APPLICABILITY_LABELS = {
  applicable: 'Only if applicable',
  buyer: 'Optional buyer document',
  confirm: 'Requirement not yet confirmed',
  suggested: 'Suggested evidence'
};

// Suggested categories only. The responsible authority and destination market decide what is actually required.
const STAGE_DOCUMENT_ITEMS = {
  1: [
    { key: 'business-info', label: 'Business or tax information', documentType: 'BUSINESS_REGISTRATION', applicability: 'applicable', text: 'Only if the relevant authority or an adviser confirms it applies to your situation.' },
    { key: 'tariff-advice', label: 'Tariff-classification advice', documentType: 'OTHER', applicability: 'confirm', requirementKeyword: 'tariff classification', text: 'Written guidance you received from SARS or a customs professional, if any.' },
    { key: 'buyer-enquiry', label: 'Buyer enquiry or agreement', documentType: 'COMMERCIAL_DOCUMENT', applicability: 'buyer', text: 'An enquiry, quote or agreement with your buyer, if you have one.' }
  ],
  2: [
    { key: 'market-access', label: 'Market-access evidence', documentType: 'MARKET_ACCESS', applicability: 'confirm', requirementKeyword: 'market access', text: 'Information from an official source about market access for your product and destination.' },
    { key: 'plant-health', label: 'Plant-health guidance or correspondence', documentType: 'MARKET_ACCESS', applicability: 'confirm', requirementKeyword: 'plant-health', text: 'Replies or guidance on whether certification, inspection or treatment applies.' },
    { key: 'import-conditions', label: 'Destination import conditions', documentType: 'MARKET_ACCESS', applicability: 'confirm', requirementKeyword: 'import conditions', text: 'Destination-country or EU import rules you checked, saved for your records.' },
    { key: 'residue-limits', label: 'Pesticide residue limits', documentType: 'MARKET_ACCESS', applicability: 'confirm', requirementKeyword: 'pesticide', text: 'Residue-limit information for the destination market.' }
  ],
  3: [
    { key: 'registration', label: 'Exporter, farm or packhouse registration evidence', documentType: 'BUSINESS_REGISTRATION', applicability: 'applicable', text: 'Only the registrations the responsible authority confirms apply to this shipment.' },
    { key: 'private-standard', label: 'Private-standard certificate', documentType: 'OTHER', applicability: 'buyer', text: 'For example GLOBALG.A.P. or SIZA, only if your buyer asks for it.' }
  ],
  4: [
    { key: 'spray-records', label: 'Pesticide and spray records', documentType: 'PRODUCTION_RECORD', applicability: 'suggested', text: 'Spray and agricultural input records for the production area.' },
    { key: 'traceability', label: 'Harvest and traceability records', documentType: 'PRODUCTION_RECORD', applicability: 'suggested', text: 'Harvest dates, lot details, orchard or block information and pest monitoring.' },
    { key: 'audit-evidence', label: 'Audit or farm-practice evidence', documentType: 'OTHER', applicability: 'buyer', text: 'Only if your buyer requests it.' }
  ],
  5: [
    { key: 'invoice', label: 'Commercial invoice', documentType: 'COMMERCIAL_DOCUMENT', applicability: 'applicable', text: 'Invoice for the shipment, where one is used.' },
    { key: 'packing-list', label: 'Packing list', documentType: 'COMMERCIAL_DOCUMENT', applicability: 'applicable', text: 'Packages, pallets and quantities, where a packing list is used.' },
    { key: 'transport', label: 'Transport or consignment note', documentType: 'CONSIGNMENT_NOTE', applicability: 'applicable', text: 'Transport, booking or consignment preparation documents.' },
    { key: 'buyer-specs', label: 'Buyer packing or labelling specification', documentType: 'OTHER', applicability: 'buyer', text: 'Only if your buyer requests one.' }
  ],
  6: [
    { key: 'inspection', label: 'Inspection record', documentType: 'INSPECTION_RECORD', applicability: 'applicable', text: 'Only if an inspection applies and you actually received a record.' },
    { key: 'official-certificate', label: 'Official certificate actually received', documentType: 'OFFICIAL_CERTIFICATE', applicability: 'applicable', hasExpiry: true, text: 'Upload only a certificate an authority issued to you, if one applies.' }
  ],
  7: [
    { key: 'customs-evidence', label: 'Customs declaration or clearance document', documentType: 'CUSTOMS_CLEARANCE', applicability: 'applicable', text: 'Upload only a customs-related document you actually received. This tracker records the file but does not verify its contents, authenticity or official acceptance.' },
    { key: 'customs-support', label: 'Supporting customs documents', documentType: 'COMMERCIAL_DOCUMENT', applicability: 'applicable', text: 'For example proof of origin, where it applies to your shipment.' }
  ],
  8: [
    { key: 'shipment-evidence', label: 'Final shipment evidence', documentType: 'OTHER', applicability: 'suggested', text: 'For example a shipping or delivery confirmation.' },
    { key: 'closure-records', label: 'Closure records', documentType: 'OTHER', applicability: 'suggested', text: 'Final correspondence, buyer receipt or notes about unresolved issues.' }
  ]
};

const OTHER_STAGE_ITEM = { key: 'other', label: 'Other supporting evidence', documentType: 'OTHER', applicability: 'suggested', text: 'Anything else that supports this stage.' };

function StageDocumentItem({ item, stageNumber, requirementId, busy, onUpload }) {
  const [file, setFile] = useState(null);
  const [notes, setNotes] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [inputKey, setInputKey] = useState(0);

  const submit = async event => {
    event.preventDefault();
    const saved = await onUpload({ file, item, stageNumber, requirementId, expiryDate, notes: notes.trim() });
    if (saved) {
      setFile(null);
      setNotes('');
      setExpiryDate('');
      setInputKey(key => key + 1);
    }
  };

  return (
    <form className="export-doc-item" onSubmit={submit}>
      <div className="export-doc-item-head">
        <b>{item.label}</b>
        <span className={`export-doc-badge ${item.applicability}`}>{APPLICABILITY_LABELS[item.applicability]}</span>
      </div>
      <p>{item.text}</p>
      <div className="export-doc-item-fields">
        <label className="export-field export-file"><span>Choose file</span>
          <input key={inputKey} type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.csv,application/pdf,image/jpeg,image/png,image/webp,text/csv" onChange={e => setFile(e.target.files?.[0] || null)} />
        </label>
        {item.hasExpiry && <Field label="Expiry date, if shown" type="date" value={expiryDate} onChange={e => setExpiryDate(e.target.value)} />}
        <Field label="Notes (optional)" value={notes} onChange={e => setNotes(e.target.value)} />
        <button className="primary" type="submit" disabled={busy || !file}>{busy ? 'Uploading…' : 'Upload'}</button>
      </div>
    </form>
  );
}

function checklistCounts(tasks = []) {
  return tasks.reduce((counts, task) => {
    if (task.status === 'COMPLETED') counts.completed += 1;
    else if (task.status === 'ACTION_REQUIRED') counts.actionRequired += 1;
    else if (task.status === 'EVIDENCE_CHECKED') counts.evidenceChecked += 1;
    else if (task.status === 'NOT_APPLICABLE') counts.notApplicable += 1;
    else counts.pending += 1;
    return counts;
  }, { completed: 0, pending: 0, actionRequired: 0, evidenceChecked: 0, notApplicable: 0 });
}

const STAGE_CONSIGNMENT_FIELDS = {
  5: [
    ['exporterName', 'Exporter or trader name'],
    ['productionUnitCode', 'Production unit, farm, orchard or packhouse code'],
    ['productionArea', 'Production area'],
    ['commodity', 'Commodity'],
    ['variety', 'Variety'],
    ['classCategory', 'Class or category'],
    ['destinationCountry', 'Destination country'],
    ['dischargePort', 'Discharge port'],
    ['packageCount', 'Number of packages'],
    ['packageType', 'Package type'],
    ['palletCount', 'Number of pallets'],
    ['consignmentNoteReference', 'Consignment note reference'],
    ['grossWeight', 'Gross weight'],
    ['netWeight', 'Net weight'],
    ['containerNumber', 'Container number'],
    ['palletIdentificationNumbers', 'Pallet identification numbers'],
    ['bookingReference', 'Booking reference'],
    ['regimeCode', 'Regime code, if applicable'],
    ['orchardBlockNumber', 'Orchard or block number, if applicable'],
    ['notes', 'Notes']
  ],
  6: [
    ['inspectionDate', 'Inspection date'],
    ['inspectionPoint', 'Inspection point'],
    ['certificationOffice', 'Certification office'],
    ['phytosanitaryInformation', 'Phytosanitary information, if applicable'],
    ['inspectionResult', 'Actual inspection result'],
    ['certificateType', 'Certificate type'],
    ['certificateNumber', 'Unique certificate number'],
    ['certificateIssueDate', 'Certificate issue date'],
    ['certificateValidity', 'Validity details'],
    ['certificateStatus', 'Recorded certificate status'],
    ['certificateConfirmationSource', 'Where or from whom you confirmed receipt'],
    ['notes', 'Notes']
  ]
};


const DOCUMENT_GROUP_LABELS = [
  ['required', 'Confirmed required documents'],
  ['conditional', 'Required only when applicable'],
  ['buyerOptional', 'Optional buyer documents'],
  ['toConfirm', 'Requirements not yet confirmed']
];

const OUTCOME_LABELS = {
  SHIPPED: 'Shipped', PARTIALLY_SHIPPED: 'Partly shipped', CANCELLED: 'Cancelled', REJECTED: 'Rejected', OTHER: 'Other',
};

function requirementStage(requirement) {
  return requirement.name.toLowerCase().includes('tariff classification') ? 1 : 2;
}

function taskStage(task, requirements) {
  if (task.requirementId == null) return task.stageNumber;
  const requirement = requirements.find(item => String(item.id) === String(task.requirementId));
  return requirement ? requirementStage(requirement) : task.stageNumber;
}

function Field({ label, value, onChange, type = 'text', required = false, disabled = false, children }) {
  return (
    <label className="export-field">
      <span>{label}{required ? ' *' : ''}</span>
      {children || <input type={type} value={value ?? ''} onChange={onChange} required={required} disabled={disabled} />}
    </label>
  );
}

function SelectField({ label, value, onChange, options, disabled = false }) {
  return (
    <label className="export-field">
      <span>{label}</span>
      <select value={value} onChange={onChange} disabled={disabled}>
        {options.map(([key, text]) => <option key={key} value={key}>{text}</option>)}
      </select>
    </label>
  );
}

function SourceList({ sources }) {
  return <ul className="export-sources">{sources.map(([label, url]) => (
    <li key={url}><a href={url} target="_blank" rel="noreferrer">{label} <span aria-hidden="true">↗</span></a></li>
  ))}</ul>;
}

function Message({ error, success }) {
  if (error) return <p className="export-message export-error" role="alert">{error}</p>;
  if (success) return <p className="export-message export-success" role="status">{success}</p>;
  return null;
}

export default function ExportReadiness() {
  const [applications, setApplications] = useState([]);
  const [application, setApplication] = useState(null);
  const [form, setForm] = useState(BLANK_APPLICATION);
  const [creating, setCreating] = useState(false);
  const [editingApplication, setEditingApplication] = useState(false);
  const [activeStage, setActiveStage] = useState(1);
  const [taskDrafts, setTaskDrafts] = useState({});
  const [consignmentForm, setConsignmentForm] = useState({});
  const [editingConsignment, setEditingConsignment] = useState(null);
  const [closureForm, setClosureForm] = useState({ shipmentReference: '', shipmentOutcome: 'SHIPPED', shipmentDate: '', unresolvedIssues: '', lessonsLearned: '' });
  const [closureError, setClosureError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  const selectedStage = STAGES[activeStage - 1];
  const stageTasks = useMemo(
    () => (application?.tasks || []).filter(task => taskStage(task, application?.requirements || []) === activeStage),
    [application, activeStage]
  );
  const stageDocuments = useMemo(
    () => (application?.documents || []).filter(document => Number(document.stageNumber) === activeStage),
    [application, activeStage]
  );
  const stageConsignmentFields = STAGE_CONSIGNMENT_FIELDS[activeStage] || [];
  const stageConsignments = useMemo(() => {
    if (!stageConsignmentFields.length) return [];
    const fieldKeys = stageConsignmentFields.map(([key]) => key);
    if (activeStage === 6) fieldKeys.push('certificateIssued');
    return (application?.consignments || []).filter(item =>
      fieldKeys.some(key => item.details[key]));
  }, [application, activeStage, stageConsignmentFields]);
  const showError = message => { setSuccess(''); setError(message); };
  const showSuccess = message => { setError(''); setSuccess(message); };
  const refresh = async id => {
    const data = await exportApi.application(id);
    setApplication(data);
    setForm({
      product: data.product || '',
      variety: data.variety || '',
      destinationCountry: data.destinationCountry || '',
      productionLocation: data.productionLocation || '',
      province: data.province || '',
      estimatedQuantity: data.estimatedQuantity ?? '',
      quantityUnit: data.quantityUnit || 'kg'
    });
    setClosureForm({
      shipmentReference: data.shipmentReference || '',
      shipmentOutcome: data.shipmentOutcome || 'SHIPPED',
      shipmentDate: data.shipmentDate || '',
      unresolvedIssues: data.unresolvedIssues || '',
      lessonsLearned: data.lessonsLearned || ''
    });
    setTaskDrafts({});
    return data;
  };
  const refreshList = async () => setApplications(await exportApi.applications());

  useEffect(() => {
    let active = true;
    exportApi.applications().then(items => {
      if (!active) return;
      setApplications(items);
      if (items.length) return exportApi.application(items[0].id).then(data => {
        if (active) {
          setApplication(data);
          setForm({
            product: data.product || '', variety: data.variety || '',
            destinationCountry: data.destinationCountry || '', productionLocation: data.productionLocation || '',
            province: data.province || '', estimatedQuantity: data.estimatedQuantity ?? '',
            quantityUnit: data.quantityUnit || 'kg'
                      });
          setClosureForm({
            shipmentReference: data.shipmentReference || '', shipmentOutcome: data.shipmentOutcome || 'SHIPPED',
            shipmentDate: data.shipmentDate || '', unresolvedIssues: data.unresolvedIssues || '',
            lessonsLearned: data.lessonsLearned || ''
          });
        }
      });
    }).catch(e => { if (active) showError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const run = async action => {
    setBusy(true);
    setError('');
    setSuccess('');
    try { await action(); }
    catch (e) { showError(e.message); }
    finally { setBusy(false); }
  };

  const startNew = () => {
    setApplication(null);
    setForm(BLANK_APPLICATION);
    setCreating(true);
    setEditingApplication(false);
    setActiveStage(1);
    setError('');
    setSuccess('');
  };

  const cancelNew = () => {
    setCreating(false);
    if (applications.length) openApplication(applications[0].id);
    else setApplication(null);
  };

  const saveApplication = event => {
    event.preventDefault();
    run(async () => {
      const data = creating
        ? await exportApi.createApplication(form)
        : await exportApi.updateApplication(application.id, form);
      await refreshList();
      await refresh(data.id);
      setCreating(false);
      setEditingApplication(false);
      showSuccess(creating ? 'Export application saved.' : 'Application details updated.');
    });
  };

  const openApplication = id => run(async () => {
    await refresh(id);
    setCreating(false);
    setEditingApplication(false);
    setActiveStage(1);
  });

  const selectStage = stageNumber => setActiveStage(stageNumber);

  const openStageUpload = () => {
    document.getElementById('export-upload-form')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  const saveTask = (task, overrides = {}) => run(async () => {
    const draft = { ...(taskDrafts[task.taskKey] || task), ...overrides };
    await exportApi.updateTask(application.id, task.taskKey, { status: draft.status, notes: draft.notes || '' });
    const data = await refresh(application.id);
    await refreshList();
    showSuccess('Checklist item status saved.');
  });

  const uploadStageDocument = async ({ file, item, stageNumber, requirementId, expiryDate, notes }) => {
    if (!file) { showError('Choose a document first.'); return false; }
    if (file.size > 10 * 1024 * 1024) { showError('Documents must be 10 MB or smaller.'); return false; }
    let saved = false;
    await run(async () => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('documentType', item.documentType);
      formData.append('stageNumber', String(stageNumber));
      if (requirementId) formData.append('requirementId', String(requirementId));
      if (expiryDate) formData.append('expiryDate', expiryDate);
      formData.append('notes', notes ? `${item.label}: ${notes}`.slice(0, 1000) : item.label);
      await exportApi.uploadDocument(application.id, formData);
      await refresh(application.id);
      saved = true;
      showSuccess(`Document uploaded to Stage ${stageNumber}.`);
    });
    return saved;
  };

  const downloadDocument = document => run(async () => {
    const blob = await exportApi.downloadDocument(document.id);
    const url = URL.createObjectURL(blob);
    const link = documentCreateLink(url, document.fileName);
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  });

  const createOrUpdateConsignment = event => {
    event.preventDefault();
    if (!stageConsignmentFields.some(([key]) => String(consignmentForm[key] || '').trim())) {
      showError('Enter at least one shipment, inspection or certificate detail before saving.');
      return;
    }
    run(async () => {
      const body = { ...(editingConsignment?.details || {}), ...consignmentForm };
      body.certificateIssued = Boolean(consignmentForm.certificateIssued);
      if (editingConsignment) {
        await exportApi.updateConsignment(application.id, editingConsignment.id, body);
      } else {
        await exportApi.createConsignment(application.id, body);
      }
      await refresh(application.id);
      setConsignmentForm({});
      setEditingConsignment(null);
      showSuccess(activeStage === 5
        ? 'Shipment and consignment details saved to your records.'
        : 'Inspection and certificate details saved to your records; they are not an AgriTech approval.');
    });
  };

  const editConsignment = item => {
    setEditingConsignment(item);
    setConsignmentForm({ ...item.details, certificateIssued: item.details.certificateIssued === 'true' });
  };

  const cancelConsignmentEdit = () => {
    setEditingConsignment(null);
    setConsignmentForm({});
  };

  const closeApplication = event => {
    event.preventDefault();
    if (application.closed || busy) return;
    if (!closureForm.shipmentReference.trim() || !closureForm.shipmentDate) {
      setClosureError('Enter the final shipment reference and the outcome date.');
      return;
    }
    setBusy(true);
    setClosureError('');
    setError('');
    setSuccess('');
    (async () => {
      try {
      await exportApi.close(application.id, closureForm);
      await refresh(application.id);
      await refreshList();
      showSuccess('Export application closed. Your final shipment outcome has been recorded.');
      setTimeout(() => document.querySelector('.export-close-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100);
      } catch (e) {
        setClosureError(e.message);
      } finally {
        setBusy(false);
      }
    })();
  };

  const updateDocument = (doc, values) => run(async () => {
    await exportApi.updateDocument(doc.id, values);
    await refresh(application.id);
    showSuccess('Document record updated for internal tracking only.');
  });

  const deleteDocument = doc => {
    if (!window.confirm('Delete this uploaded document? This cannot be undone.')) return;
    run(async () => {
      await exportApi.deleteDocument(doc.id);
      await refresh(application.id);
      showSuccess('Document deleted.');
    });
  };

  const formField = key => event => setForm(old => ({ ...old, [key]: event.target.value }));
  const taskValue = task => taskDrafts[task.taskKey] || task;
  const setTaskValue = (task, key, value) => setTaskDrafts(old => ({
    ...old,
    [task.taskKey]: { ...(old[task.taskKey] || task), [key]: value }
  }));
  const isReadOnly = application?.closed;
  const overallCounts = checklistCounts(application?.tasks || []);
  const recordedTaskCount = (application?.tasks || []).filter(task => task.statusRecorded).length;

  return (
    <section className="export-page">
      <div className="page-head">
        <div>
          <p className="eyebrow">EXPORT READINESS</p>
          <h1>Prepare your export</h1>
          <p className="muted">Track your export journey from preparation to shipment. Follow key requirements, monitor your progress, and keep all your supporting documents organised in one place.</p>
        </div>
        <button className="primary" type="button" onClick={startNew}>＋ New application</button>
      </div>

      <Message error={error} success={success} />

      {applications.length > 0 && !creating && (
        <div className="export-select-row">
          <label htmlFor="export-application-select">Your applications</label>
          <select id="export-application-select" value={application?.id || ''} onChange={event => openApplication(event.target.value)}>
            {applications.map(item => <option key={item.id} value={item.id}>
              {item.product} → {item.destinationCountry} · {item.closed ? 'Closed' : `${item.progress}% checklist`}
            </option>)}
          </select>
          {!isReadOnly && application && <button type="button" className="secondary" onClick={() => setEditingApplication(v => !v)}>
            {editingApplication ? 'Cancel edit' : 'Edit details'}
          </button>}
        </div>
      )}

      {loading && <div className="panel export-empty"><p>Loading your export records…</p></div>}

      {!loading && !application && !creating && (
        <div className="panel export-empty">
          <span aria-hidden="true">↗</span>
          <h2>Start with one export application</h2>
          <p>Capture the product, destination and farm details, then work through the eight stages. You can save and return later.</p>
          <button type="button" className="primary" onClick={startNew}>Create application</button>
        </div>
      )}

      {(creating || editingApplication) && (
        <form className="panel export-form-panel" onSubmit={saveApplication}>
          <div className="export-panel-heading">
            <div><p className="eyebrow">{creating ? 'NEW RECORD' : 'APPLICATION DETAILS'}</p><h2>{creating ? 'Export application' : 'Update application'}</h2></div>
            {creating
              ? <button type="button" className="secondary" onClick={cancelNew}>Cancel</button>
              : <button type="button" className="secondary" onClick={() => setEditingApplication(false)}>Cancel</button>}
          </div>
          <div className="export-form-grid">
            <Field label="Product or commodity" value={form.product} onChange={formField('product')} required />
            <Field label="Variety" value={form.variety} onChange={formField('variety')} />
            <Field label="Destination country or market" value={form.destinationCountry} onChange={formField('destinationCountry')} required />
            <Field label="Production location / farm" value={form.productionLocation} onChange={formField('productionLocation')} required />
            <Field label="Province" value={form.province} onChange={formField('province')} required />
            <Field label="Estimated quantity" type="number" value={form.estimatedQuantity} onChange={formField('estimatedQuantity')} required>
              <div className="export-input-combo">
                <input type="number" min="0.001" step="any" value={form.estimatedQuantity} onChange={formField('estimatedQuantity')} required />
                <select value={form.quantityUnit} onChange={formField('quantityUnit')} aria-label="Quantity unit">
                  {['kg', 'tonne', 'crate', 'box', 'pallet', 'other'].map(unit => <option key={unit}>{unit}</option>)}
                </select>
              </div>
            </Field>
          </div>
          <button className="primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save application'}</button>
        </form>
      )}

      {application && !creating && !editingApplication && (
        <>
          <div className="export-progress panel">
            {application.closed && <div className="export-closed-banner">
              <span><b>Closed</b> · {OUTCOME_LABELS[application.shipmentOutcome] || application.shipmentOutcome}</span>
              <button type="button" className="secondary" onClick={() => document.querySelector('.export-close-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>View final summary</button>
            </div>}
            <div className="export-progress-head">
              <div><p className="eyebrow">{application.closed ? 'CLOSED RECORD' : 'PREPARATION CHECKLIST PROGRESS'}</p>
                <p className="muted">{application.estimatedQuantity} {application.quantityUnit} · {application.productionLocation}{application.province ? `, ${application.province}` : ''}</p>
              </div>
              <strong>{application.progress}%<small> checklist</small></strong>
            </div>
            <div className="export-progress-track"><span style={{ width: `${application.progress}%` }} /></div>
            <div className="export-checklist-counts">
              <span>Completed: {overallCounts.completed}</span>
              <span>Pending: {overallCounts.pending}</span>
              <span>Action required: {overallCounts.actionRequired}</span>
              <span>Not applicable: {overallCounts.notApplicable}</span>
              {overallCounts.evidenceChecked > 0 && <span>Checked for internal tracking: {overallCounts.evidenceChecked}</span>}
            </div>
            <p className="export-progress-explanation">
              {application.allChecklistItemsRecorded
                ? 'All checklist items have a recorded status.'
                : `${Math.max(0, (application.tasks || []).length - recordedTaskCount)} checklist items still need a recorded status.`}
              {' '}{application.progressDescription || 'Checklist completion does not mean official requirements have been satisfied.'}
            </p>
            <div className="export-small-stats"><span>Created {new Date(application.createdAt).toLocaleDateString()}</span></div>
          </div>

          <div className="export-workflow">
            <aside className="panel export-stage-list">
              <p className="eyebrow">YOUR WORKFLOW</p>
              {STAGES.map((stage, index) => {
                const num = index + 1;
                const items = application.tasks.filter(task => taskStage(task, application.requirements) === num);
                const counts = checklistCounts(items);
                return <button key={stage.name} type="button" className={activeStage === num ? 'active' : ''} aria-pressed={activeStage === num} onClick={() => selectStage(num)}>
                  <i>{num}</i><span>{stage.name}<small>{counts.completed} done · {counts.pending} pending · {counts.actionRequired} action · {counts.notApplicable} N/A</small></span>
                </button>;
              })}
            </aside>
            <section className="panel export-stage-panel">
              <div className="export-stage-heading"><div><p className="eyebrow">STAGE {activeStage} OF 8</p><h2>{selectedStage.name}</h2></div>
                <span className="tag">{checklistCounts(stageTasks).completed} completed · {checklistCounts(stageTasks).pending} pending · {checklistCounts(stageTasks).actionRequired} action · {checklistCounts(stageTasks).notApplicable} N/A</span></div>
              <p className="export-meaning">{selectedStage.meaning}</p>
              <div className="export-guidance">
                <div><h3>What to do</h3><p>{selectedStage.actions}</p></div>
                <div><h3>How to do it</h3><p>{selectedStage.how}</p></div>
                <div className="export-stage-document-guidance"><h3>Documents and evidence</h3>
                  <div className="export-document-groups">
                    {DOCUMENT_GROUP_LABELS.map(([key, label]) => {
                      const items = selectedStage.documentGroups[key];
                      return <div key={key}>
                        <b>{activeStage === 7 && key === 'required' ? 'Documents to confirm for your shipment' : label}</b>
                        {items.length > 0
                          ? <ul>{items.map(item => <li key={item}>{item}</li>)}</ul>
                          : <p>{key === 'required'
                            ? activeStage === 7
                              ? 'Export declarations, invoices, packing lists, transport documents and proof of origin may apply depending on your shipment. Confirm the exact documents with SARS or your customs professional.'
                              : 'No universally required document is confirmed here. Check with the responsible authority.'
                            : 'None listed for this stage.'}</p>}
                      </div>;
                    })}
                  </div>
                </div>
                <div><h3>Who is responsible</h3><p>{selectedStage.responsibility}</p></div>
              </div>
              <div className="export-next"><b>What happens next</b><p>{selectedStage.next}</p></div>
              <div className="export-source-block"><h3>Sources to check</h3><SourceList sources={selectedStage.sources} /></div>
              <div className="export-task-list">
                <div className="export-subheading"><h3>Checklist and notes</h3><span>Saved to your account</span></div>
                {stageTasks.map(task => {
                  const draft = taskValue(task);
                  const requirement = application.requirements.find(item => item.id === task.requirementId);
                  return <article key={task.taskKey} className="export-task">
                    <div className="export-task-head"><b>{task.title}</b></div>
                    {requirement && <p className="export-task-context">{requirement.followUp} {requirement.sourceUrl && <a href={requirement.sourceUrl} target="_blank" rel="noreferrer">Open official source ↗</a>}</p>}
                    <div className="export-task-fields">
                      <SelectField label="Status" value={draft.status} disabled={isReadOnly} onChange={event => {
                        setTaskValue(task, 'status', event.target.value);
                        if (!isReadOnly) saveTask(task, { status: event.target.value });
                      }} options={TASK_STATUSES} />
                      <Field label="Notes or follow-up" value={draft.notes || ''} disabled={isReadOnly} onChange={event => setTaskValue(task, 'notes', event.target.value)} />
                    </div>
                    {!isReadOnly && <button className="secondary export-save-task" type="button" disabled={busy} onClick={() => saveTask(task)}>Save checklist item</button>}
                  </article>;
                })}
              </div>
              {stageConsignmentFields.length > 0 && (
                <div className="export-stage-records">
                  <div className="export-subheading">
                    <h3>{activeStage === 5 ? 'Shipment and consignment details' : 'Inspection and certificate details'}</h3>
                    <span>{stageConsignments.length} records</span>
                  </div>
                  <p className="export-record-note">
                    {activeStage === 5
                      ? 'Record known shipment, package, booking and container details here.'
                      : 'Record actual inspection results and certificate details only after they are confirmed. AgriTech does not issue or verify certificates.'}
                  </p>
                  {!isReadOnly && <form className="export-stage-record-form" onSubmit={createOrUpdateConsignment}>
                    <div className="export-form-grid">
                      {stageConsignmentFields
                        .filter(([key]) => activeStage !== 6 || !key.startsWith('certificate') || consignmentForm.certificateIssued)
                        .map(([key, label]) => <Field
                          key={key}
                          label={label}
                          type={['inspectionDate', 'certificateIssueDate'].includes(key) ? 'date' : 'text'}
                          value={consignmentForm[key] || ''}
                          onChange={event => setConsignmentForm({ ...consignmentForm, [key]: event.target.value })}
                        />)}
                    </div>
                    {activeStage === 6 && <label className="export-check">
                      <input
                        type="checkbox"
                        checked={Boolean(consignmentForm.certificateIssued)}
                        disabled={editingConsignment?.details.certificateIssued === 'true'}
                        onChange={event => setConsignmentForm({ ...consignmentForm, certificateIssued: event.target.checked })}
                      />
                      I have actually received an official certificate for this consignment.
                    </label>}
                    <div className="export-form-actions">
                      <button className="primary" type="submit" disabled={busy}>
                        {busy ? 'Saving…' : editingConsignment
                          ? 'Update record'
                          : activeStage === 5 ? 'Save shipment details' : 'Save inspection details'}
                      </button>
                      {editingConsignment && <button type="button" className="secondary" onClick={cancelConsignmentEdit}>Cancel edit</button>}
                    </div>
                  </form>}
                  {stageConsignments.length > 0 ? <div className="export-stage-record-list">
                    {stageConsignments.map(item => <article key={item.id} className="export-stage-record">
                      <div className="export-stage-record-heading">
                        <b>{item.details.commodity || application.product}{item.details.variety ? ` · ${item.details.variety}` : ''}</b>
                        {!isReadOnly && <button className="secondary" type="button" onClick={() => editConsignment(item)}>Edit record</button>}
                      </div>
                      <div className="export-consignment-values">
                        {stageConsignmentFields.filter(([key]) => item.details[key]).map(([key, label]) =>
                          <p key={key}><b>{label}:</b> {item.details[key]}</p>)}
                        {activeStage === 6 && item.details.certificateIssued === 'true' && <p><b>Official certificate received:</b> Farmer-recorded</p>}
                      </div>
                      {activeStage === 6 && <small>{item.certificateNotice}</small>}
                    </article>)}
                  </div> : <p className="export-empty-inline">No {activeStage === 5 ? 'shipment' : 'inspection or certificate'} details saved yet.</p>}
                </div>
              )}
            </section>
          </div>

          <section className="panel export-documents">
            <div className="export-panel-heading"><div><p className="eyebrow">DOCUMENTS</p><h2>Supporting evidence</h2></div><span className="tag">{application.documents.length} files</span></div>
            <p className="muted">Select a stage and upload the documents you have. Maximum 10 MB each: PDF, JPEG, PNG, WebP or CSV.</p>
            <p className="export-doc-status-help">Uploaded means the file is saved. Checked for internal tracking records your review only; it does not confirm authenticity or official acceptance.</p>
            <div className="export-doc-stage-tabs" role="tablist" aria-label="Choose a stage">
              {STAGES.map((stage, index) => {
                const num = index + 1;
                const count = application.documents.filter(doc => Number(doc.stageNumber) === num).length;
                const progress = checklistCounts(application.tasks.filter(task => taskStage(task, application.requirements) === num));
                return <button key={stage.name} type="button" role="tab" aria-selected={activeStage === num} className={activeStage === num ? 'active' : ''} onClick={() => selectStage(num)}>
                  <b>Stage {num}</b><span>{stage.name}<small>{progress.completed} completed · {progress.pending} pending · {progress.actionRequired} action · {progress.notApplicable} N/A</small></span>{count > 0 && <em>{count}</em>}
                </button>;
              })}
            </div>
            <div id="export-upload-form" className="export-doc-items">
              <h3>Stage {activeStage}: {selectedStage.name}</h3>
              {isReadOnly
                ? <p className="export-empty-inline">This application is closed, so new uploads are turned off.</p>
                : [...STAGE_DOCUMENT_ITEMS[activeStage], OTHER_STAGE_ITEM].map(item => {
                  const requirement = item.requirementKeyword
                    ? application.requirements.find(entry => entry.name.toLowerCase().includes(item.requirementKeyword))
                    : null;
                  return <StageDocumentItem key={`${activeStage}-${item.key}`} item={item} stageNumber={activeStage}
                    requirementId={requirement?.id} busy={busy} onUpload={uploadStageDocument} />;
                })}
            </div>
            <div className="export-subheading"><h3>Uploaded for Stage {activeStage}</h3><span>{stageDocuments.length} files</span></div>
            {stageDocuments.length > 0 ? <div className="export-doc-list">
              {stageDocuments.map(doc => <article className="export-document" key={doc.id}>
                <div className="export-document-main"><b>{doc.fileName}</b><span>{DOC_TYPES.find(([key]) => key === doc.documentType)?.[1] || doc.documentType}</span>
                  <small>Status: {DOC_STATUSES.find(([key]) => key === doc.recordStatus)?.[1] || 'Uploaded'} · Stage {doc.stageNumber} · {new Date(doc.uploadedAt).toLocaleDateString()}</small>
                </div>
                <div className="export-document-actions">
                  <button type="button" className="secondary" disabled={busy} onClick={() => downloadDocument(doc)}>Download</button>
                  {!isReadOnly && <select aria-label={`Recordkeeping status for ${doc.fileName}`} value={doc.recordStatus} onChange={e => updateDocument(doc, { recordStatus: e.target.value })}>
                    {DOC_STATUSES.map(([key, text]) => <option key={key} value={key}>{text}</option>)}
                  </select>}
                  {!isReadOnly && doc.documentType !== 'OFFICIAL_CERTIFICATE' && <button type="button" className="export-delete" onClick={() => deleteDocument(doc)}>Delete</button>}
                </div>
                {doc.expiryDate && <small>Expiry date recorded: {doc.expiryDate}</small>}
                {doc.notes && <p>{doc.notes}</p>}
              </article>)}
            </div> : <p className="export-empty-inline">No documents uploaded for this stage yet.</p>}
            {application.documents.some(doc => !doc.stageNumber) && <>
              <div className="export-subheading"><h3>Not linked to a stage</h3><span>{application.documents.filter(doc => !doc.stageNumber).length} files</span></div>
              <div className="export-doc-list">
                {application.documents.filter(doc => !doc.stageNumber).map(doc => <article className="export-document" key={doc.id}>
                  <div className="export-document-main"><b>{doc.fileName}</b><span>{DOC_TYPES.find(([key]) => key === doc.documentType)?.[1] || doc.documentType}</span>
                    <small>Status: {DOC_STATUSES.find(([key]) => key === doc.recordStatus)?.[1] || 'Uploaded'} · {new Date(doc.uploadedAt).toLocaleDateString()}</small></div>
                  <div className="export-document-actions"><button type="button" className="secondary" disabled={busy} onClick={() => downloadDocument(doc)}>Download</button></div>
                </article>)}
              </div>
            </>}
          </section>

          <section className="panel export-close-panel">
            <div className="export-panel-heading"><div><p className="eyebrow">{application.closed ? 'FINAL SUMMARY' : 'STAGE 8'}</p><h2>{application.closed ? 'Export application closed' : 'Record the shipment outcome'}</h2></div>
              {application.closed && <span className="export-closed-badge">Closed</span>}</div>
            {application.closed ? <div className="export-final-summary">
              <p className="export-final-title">{application.product} → {application.destinationCountry}</p>
              <div className="export-consignment-values">
                <p><b>Quantity:</b> {application.estimatedQuantity} {application.quantityUnit}</p>
                <p><b>Final outcome:</b> {OUTCOME_LABELS[application.shipmentOutcome] || application.shipmentOutcome}</p>
                <p><b>Consignment reference:</b> {application.shipmentReference}</p>
                <p><b>Outcome date:</b> {application.shipmentDate}</p>
                <p><b>Closed on:</b> {application.closedAt ? new Date(application.closedAt).toLocaleDateString() : '—'}</p>
                <p><b>Unresolved issues:</b> {application.unresolvedIssues || 'None recorded'}</p>
                <p><b>Lessons learned:</b> {application.lessonsLearned || 'None recorded'}</p>
              </div>
              <p className="muted">This application is closed for tracking. Its records can be viewed but not edited.</p>
            </div> : <form className="export-form-grid" onSubmit={closeApplication}>
              <Field label="Final shipment or consignment reference" value={closureForm.shipmentReference} onChange={e => setClosureForm({ ...closureForm, shipmentReference: e.target.value })} required />
              <SelectField label="Actual outcome" value={closureForm.shipmentOutcome} onChange={e => setClosureForm({ ...closureForm, shipmentOutcome: e.target.value })} options={[
                ['SHIPPED', 'Shipped'], ['PARTIALLY_SHIPPED', 'Partly shipped'], ['CANCELLED', 'Cancelled'], ['REJECTED', 'Rejected'], ['OTHER', 'Other actual outcome']
              ]} />
              <Field label="Outcome date" type="date" value={closureForm.shipmentDate} onChange={e => setClosureForm({ ...closureForm, shipmentDate: e.target.value })} required />
              <Field label="Unresolved issues" value={closureForm.unresolvedIssues} onChange={e => setClosureForm({ ...closureForm, unresolvedIssues: e.target.value })} />
              <Field label="Lessons learned" value={closureForm.lessonsLearned} onChange={e => setClosureForm({ ...closureForm, lessonsLearned: e.target.value })} />
              <div className="export-close-action">
                {closureError && <div className="export-error export-inline-error" role="alert">
                  <span>{closureError}</span>
                  {closureError.toLowerCase().includes('stage 7') && <button type="button" className="secondary" onClick={() => {
                    selectStage(7);
                    document.querySelector('.export-documents')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  }}>Go to Stage 7 Customs clearance</button>}
                </div>}
                <button className="primary" type="submit" disabled={busy}>{busy ? 'Saving…' : 'Close application with this actual outcome'}</button></div>
            </form>}
          </section>
        </>
      )}

      <section className="panel export-authorities">
        <p className="eyebrow">WHO DOES WHAT</p><h2>Different authorities have different roles</h2>
        <div className="export-authority-grid">
          <p><b>PPECB</b><br />Inspection, certification and cold-chain functions for applicable perishable exports. It does not handle every export requirement.</p>
          <p><b>Department of Agriculture / plant-health authority</b><br />Export standards, market-access protocols and official plant-health certification, depending on the product. Confirm the current process on the official <a href="https://www.gov.za/services/services-organisations/export-permits/ecertification-ecert" target="_blank" rel="noreferrer">eCertification guidance ↗</a>.</p>
          <p><b>SARS Customs</b><br />South African exporter registration, tariff classification and export customs processes.</p>
          <p><b>Destination authorities</b><br />Set the import conditions, permits, plant-health requirements and other border rules for their market.</p>
          <p><b>Buyers and private standards</b><br />May request commercial specifications or standards such as GLOBALG.A.P. or SIZA. These are distinct from legal requirements.</p>
        </div>
        <p className="muted">Confirm current requirements with the authority responsible for your product, route and transaction.</p>
      </section>
    </section>
  );
}

function documentCreateLink(url, fileName) {
  const link = window.document.createElement('a');
  link.href = url;
  link.download = fileName;
  return link;
}
