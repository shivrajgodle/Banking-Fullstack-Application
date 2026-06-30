/**
 * ToastService — Lightweight in-app toast notifications
 * Manages a list of toasts shown in the top-right corner.
 */
import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  duration?: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  /** Angular 17 signal — reactive list of active toasts */
  toasts = signal<Toast[]>([]);

  show(type: Toast['type'], title: string, message: string, duration = 4000): void {
    const id = Date.now().toString();
    const toast: Toast = { id, type, title, message, duration };
    this.toasts.update(current => [...current, toast]);
    // Auto-remove after duration
    setTimeout(() => this.remove(id), duration);
  }

  success(title: string, message: string): void {
    this.show('success', title, message);
  }

  error(title: string, message: string): void {
    this.show('error', title, message, 6000);
  }

  warning(title: string, message: string): void {
    this.show('warning', title, message, 5000);
  }

  info(title: string, message: string): void {
    this.show('info', title, message);
  }

  remove(id: string): void {
    this.toasts.update(current => current.filter(t => t.id !== id));
  }
}
