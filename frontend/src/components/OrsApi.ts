import {json, request} from '../api/client';

export interface OrsLocation {
    label: string;
    lat: number;
    lon: number;
}

export const findPlaces = (query: string) =>
  request<OrsLocation[]>(`/api/ors?q=${encodeURIComponent(query)}`);