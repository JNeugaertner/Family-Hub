import {json, request} from '../api/client';
import{WeatherLocation} from '../weather/api';

export interface OrsLocation {
    label: string;
    lat: number;
    lon: number;
}

export const findPlaces = (query: string) =>
  request<OrsLocation[]>(`/api/ors?q=${encodeURIComponent(query)}`);

export const getTravelTime = (startLat: number, startLon: number, endLat: number, endLon: number) =>
  request<number>(`/api/ors/travelTime?startLat=${startLat}&startLon=${startLon}&endLat=${endLat}&endLon=${endLon}`);

export interface Settings {
  weatherLocation: WeatherLocation | null;
}

export const getSettings = () => request<Settings>('/api/settings');
