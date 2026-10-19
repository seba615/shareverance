import type { OcrResultResponse, ReceiptUploadResponse } from './api/java';

interface ReceiptLoaderOptions {
  upload: (organizationId: number, file: File) => Promise<ReceiptUploadResponse>;
  processOcr: (organizationId: number, receiptId: string) => Promise<OcrResultResponse>;
  onUpload: (receipt: ReceiptUploadResponse) => void;
  onResult: (result: OcrResultResponse) => void;
  onError: (error: unknown) => void;
  onOcrError: () => void;
  onLoading: (loading: boolean) => void;
}

export function createReceiptLoader(options: ReceiptLoaderOptions) {
  let generation = 0;
  let loading = false;
  return {
    invalidate() {
      generation++;
      loading = false;
      options.onLoading(false);
    },
    isLoading: () => loading,
    async load(organizationId: number, file: File, isCurrentOrganization: () => boolean) {
      const current = ++generation;
      const isCurrent = () => current === generation && isCurrentOrganization();
      loading = true;
      options.onLoading(true);
      try {
        const receipt = await options.upload(organizationId, file);
        if (!isCurrent()) return;
        options.onUpload(receipt);
        try {
          const result = await options.processOcr(organizationId, receipt.receiptId);
          if (isCurrent()) options.onResult(result);
        } catch {
          if (isCurrent()) options.onOcrError();
        }
      } catch (error) {
        if (isCurrent()) options.onError(error);
      } finally {
        if (isCurrent()) {
          loading = false;
          options.onLoading(false);
        }
      }
    },
  };
}
