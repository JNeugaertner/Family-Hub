import { json, request } from '../api/client';

export interface WeatherLocation {
  name: string;
  state: string | null;
  country: string | null;
  lat: number;
  lon: number;
}

export interface AdviceItem {
  icon: string;
  label: string;
  reason: string;
}

// Temperaturen in °C, Wind in km/h; icon ist der Symbol-Code von OpenWeather, z. B. "10d"
export interface WeatherReport {
  location: string;
  current: { temp: number; feelsLike: number; description: string; icon: string; humidity: number; wind: number };
  high: number;
  low: number;
  days: { date: string; high: number; low: number; description: string; icon: string; rainChance: number }[];
  advice: { kind: 'rain' | 'heat' | 'cold' | 'sun' | 'mild'; title: string; items: AdviceItem[] };
  observedAt: string;
}

// 409: noch kein Wohnort eingestellt; 503: OpenWeather nicht eingerichtet oder nicht erreichbar
export const getWeather = () => request<WeatherReport>('/api/weather');

export const findPlaces = (query: string) =>
  request<WeatherLocation[]>(`/api/weather/places?q=${encodeURIComponent(query)}`);

export const setWeatherLocation = (location: WeatherLocation) =>
  request<WeatherLocation>('/api/weather/location', { method: 'PUT', body: json(location) });

export const removeWeatherLocation = () => request<void>('/api/weather/location', { method: 'DELETE' });

// Symbol-Codes von OpenWeather (https://openweathermap.org/weather-conditions) als Emoji
const ICONS: Record<string, string> = {
  '01': '☀️', '02': '🌤️', '03': '⛅', '04': '☁️', '09': '🌧️', '10': '🌦️', '11': '⛈️', '13': '❄️', '50': '🌫️',
};

export const weatherEmoji = (icon: string | null | undefined) =>
  icon === '01n' ? '🌙' : (icon && ICONS[icon.substring(0, 2)]) || '🌡️';
