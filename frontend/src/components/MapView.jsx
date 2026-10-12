import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const SOUTH_AFRICA = [-28.5, 25.0];
const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

function baseMap(el) {
  const map = L.map(el, { scrollWheelZoom: false }).setView(SOUTH_AFRICA, 5);
  L.tileLayer(TILES, { maxZoom: 19, attribution: ATTRIBUTION }).addTo(map);
  return map;
}

/** Map of listings. Dots use circle markers so no marker image files are needed. Tap a dot to select a listing. */
export function ListingMap({ listings, me, selectedId, onSelect, height = 380 }) {
  const el = useRef(null);
  const map = useRef(null);
  const layer = useRef(null);
  const pick = useRef(onSelect);
  pick.current = onSelect;

  useEffect(() => {
    map.current = baseMap(el.current);
    layer.current = L.layerGroup().addTo(map.current);
    return () => { map.current.remove(); map.current = null; };
  }, []);

  // Redraw the dots whenever the data or the selection changes.
  useEffect(() => {
    if (!map.current) return;
    layer.current.clearLayers();
    listings.filter((l) => l.latitude != null).forEach((l) => {
      const selected = l.id === selectedId;
      L.circleMarker([l.latitude, l.longitude], {
        radius: selected ? 12 : 9, color: '#0d5c45', weight: selected ? 3 : 2,
        fillColor: l.onSale ? '#d49a1d' : '#2c7257', fillOpacity: 0.9
      }).addTo(layer.current)
        .bindTooltip(`${l.product} - R${Number(l.effectivePrice).toFixed(2)}/${l.unit}`)
        .on('click', () => pick.current(l));
    });
    if (me) {
      L.circleMarker([me.lat, me.lng], { radius: 8, color: '#1d4ed8', weight: 3, fillColor: '#3b82f6', fillOpacity: 1 })
        .addTo(layer.current).bindTooltip('You are here');
    }
  }, [listings, me, selectedId]);

  // Zoom to fit only when the set of results changes (not on every selection).
  useEffect(() => {
    if (!map.current) return;
    const pts = listings.filter((l) => l.latitude != null).map((l) => [l.latitude, l.longitude]);
    if (me) pts.push([me.lat, me.lng]);
    if (pts.length === 1) map.current.setView(pts[0], 11);
    else if (pts.length > 1) map.current.fitBounds(pts, { padding: [30, 30], maxZoom: 12 });
  }, [listings, me]);

  return <div className="map-box"><div ref={el} style={{ height, width: '100%' }} /></div>;
}

/** Small map where the farmer taps to set where the produce can be collected. */
export function MapPicker({ value, onChange, height = 260 }) {
  const el = useRef(null);
  const map = useRef(null);
  const marker = useRef(null);
  const change = useRef(onChange);
  change.current = onChange;

  useEffect(() => {
    map.current = baseMap(el.current);
    map.current.on('click', (e) => change.current({ lat: e.latlng.lat, lng: e.latlng.lng }));
    return () => { map.current.remove(); map.current = null; marker.current = null; };
  }, []);

  useEffect(() => {
    if (!map.current || !value) return;
    if (marker.current) marker.current.setLatLng([value.lat, value.lng]);
    else {
      marker.current = L.circleMarker([value.lat, value.lng], { radius: 10, color: '#0d5c45', weight: 3, fillColor: '#2c7257', fillOpacity: 0.9 }).addTo(map.current);
    }
    map.current.setView([value.lat, value.lng], Math.max(map.current.getZoom(), 11));
  }, [value]);

  return <div className="map-box"><div ref={el} style={{ height, width: '100%' }} /></div>;
}
