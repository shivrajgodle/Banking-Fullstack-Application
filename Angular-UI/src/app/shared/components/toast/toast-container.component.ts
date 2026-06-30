/**
 * ToastContainerComponent — Renders all active toast notifications
 * Positioned fixed top-right, rendered in AppComponent so it's always visible.
 */
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ToastService, Toast } from "../../../core/services/toast.service";

@Component({
  selector: "app-toast-container",
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container">
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="toast"
          [class]="'toast-' + toast.type"
          (click)="toastService.remove(toast.id)"
        >
          <div class="toast-icon">
            @switch (toast.type) {
              @case ("success") {
                ✓
              }
              @case ("error") {
                ✕
              }
              @case ("warning") {
                ⚠
              }
              @case ("info") {
                ℹ
              }
            }
          </div>
          <div class="toast-body">
            <div class="toast-title">{{ toast.title }}</div>
            <div class="toast-message">{{ toast.message }}</div>
          </div>
          <button class="toast-close" (click)="toastService.remove(toast.id)">
            ×
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .toast-container {
        position: fixed;
        top: 24px;
        right: 24px;
        z-index: 9999;
        display: flex;
        flex-direction: column;
        gap: 10px;
        max-width: 360px;
        width: 100%;
      }

      .toast {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        padding: 14px 16px;
        border-radius: var(--radius-md);
        background: var(--bg-elevated);
        border: 1px solid var(--border-subtle);
        box-shadow: var(--shadow-lg);
        cursor: pointer;
        animation: slideInRight 0.3s ease both;
        transition: opacity 0.2s;

        &:hover {
          opacity: 0.9;
        }
      }

      .toast-success {
        border-left: 3px solid var(--success);
      }
      .toast-error {
        border-left: 3px solid var(--error);
      }
      .toast-warning {
        border-left: 3px solid var(--warning);
      }
      .toast-info {
        border-left: 3px solid var(--info);
      }

      .toast-icon {
        width: 24px;
        height: 24px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.8125rem;
        font-weight: 700;
        flex-shrink: 0;
      }

      .toast-success .toast-icon {
        background: var(--success-bg);
        color: var(--success);
      }
      .toast-error .toast-icon {
        background: var(--error-bg);
        color: var(--error);
      }
      .toast-warning .toast-icon {
        background: var(--warning-bg);
        color: var(--warning);
      }
      .toast-info .toast-icon {
        background: var(--info-bg);
        color: var(--info);
      }

      .toast-body {
        flex: 1;
        min-width: 0;
      }

      .toast-title {
        font-family: var(--font-display);
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--text-primary);
        margin-bottom: 2px;
      }

      .toast-message {
        font-size: 0.8125rem;
        color: var(--text-secondary);
        line-height: 1.4;
      }

      .toast-close {
        background: none;
        border: none;
        color: var(--text-muted);
        font-size: 1.25rem;
        cursor: pointer;
        padding: 0 4px;
        line-height: 1;
        flex-shrink: 0;
        &:hover {
          color: var(--text-primary);
        }
      }

      @keyframes slideInRight {
        from {
          opacity: 0;
          transform: translateX(24px);
        }
        to {
          opacity: 1;
          transform: translateX(0);
        }
      }
    `,
  ],
})
export class ToastContainerComponent {
  toastService = inject(ToastService);
}
