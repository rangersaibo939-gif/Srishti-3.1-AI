import { PersistedTask, PersistedTaskStep, TaskStatus } from './domain';

const STORAGE_KEY = 'opendroid_phase1_room_tasks';

/**
 * Room SQLite Simulation Layer for Web / Sandbox
 * Mirrors Android Room DAO behavior with synchronous disk persistence
 */
export class RoomTaskRepository {
  private static getStore(): Record<string, PersistedTask> {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      return data ? JSON.parse(data) : {};
    } catch {
      return {};
    }
  }

  private static saveStore(store: Record<string, PersistedTask>) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
    } catch (e) {
      console.error('Failed to write to Room Task Store', e);
    }
  }

  public static async insertTask(task: PersistedTask): Promise<void> {
    const store = this.getStore();
    store[task.id] = { ...task, updatedAt: Date.now() };
    this.saveStore(store);
  }

  public static async updateTaskStatus(taskId: string, status: TaskStatus, errorMessage?: string, finalResponse?: string): Promise<void> {
    const store = this.getStore();
    if (store[taskId]) {
      store[taskId].status = status;
      if (errorMessage !== undefined) store[taskId].errorMessage = errorMessage;
      if (finalResponse !== undefined) store[taskId].finalResponse = finalResponse;
      store[taskId].updatedAt = Date.now();
      this.saveStore(store);
    }
  }

  public static async appendStep(taskId: string, step: PersistedTaskStep): Promise<void> {
    const store = this.getStore();
    if (store[taskId]) {
      // Check duplicate idempotency key
      const existing = store[taskId].steps.find(s => s.idempotencyKey === step.idempotencyKey);
      if (existing) {
        console.warn(`Idempotent duplicate step ignored: ${step.idempotencyKey}`);
        return;
      }
      store[taskId].steps.push(step);
      store[taskId].updatedAt = Date.now();
      this.saveStore(store);
    }
  }

  public static async updateStepStatus(
    taskId: string, 
    idempotencyKey: string, 
    status: TaskStatus,
    resultJson?: string,
    verified: boolean = false
  ): Promise<void> {
    const store = this.getStore();
    if (store[taskId]) {
      const step = store[taskId].steps.find(s => s.idempotencyKey === idempotencyKey);
      if (step) {
        step.status = status;
        if (resultJson) step.resultJson = resultJson;
        step.verified = verified;
        store[taskId].updatedAt = Date.now();
        this.saveStore(store);
      }
    }
  }

  public static async getTask(taskId: string): Promise<PersistedTask | null> {
    const store = this.getStore();
    return store[taskId] || null;
  }

  public static async getAllTasks(): Promise<PersistedTask[]> {
    const store = this.getStore();
    return Object.values(store).sort((a, b) => b.createdAt - a.createdAt);
  }

  public static async clearAll(): Promise<void> {
    localStorage.removeItem(STORAGE_KEY);
  }
}
