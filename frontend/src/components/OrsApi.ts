import {json, request} from '../api/client';

export interface OrsLocation {
    label: string;
    lat: number;
    lon: number;
}

export const findPlaces = (query: string) =>
  request<OrsLocation[]>(`/api/ors?q=${encodeURIComponent(query)}`);

export const getTravelTime = (startLat: number, startLon: number, endLat: number, endLon: number) =>
  request<number>(`/api/ors/travelTime?startLat=${startLat}&startLon=${startLon}&endLat=${endLat}&endLon=${endLon}`);