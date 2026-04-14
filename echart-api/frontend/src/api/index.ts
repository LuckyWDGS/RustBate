import axios from 'axios';

const api = axios.create({
  baseURL: 'http://127.0.0.1:3000/api',
  timeout: 10000,
});

export interface PowerData {
  equipment: string[][];
  defects: string[][];
  defects_detail: string[][];
  maintenance_summary: string[][];
  maintenance: string[][];
}

export interface MowData {
  equipment: string[][];
  defects: string[][];
  maintenance: string[][];
}

export const getPowerData = () => api.get<PowerData>('/power/data');
export const getMowData = () => api.get<MowData>('/mow/data');
