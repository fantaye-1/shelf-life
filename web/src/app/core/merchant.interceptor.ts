import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MerchantStore } from './merchant.store';

export const MERCHANT_HEADER = 'X-Merchant-Id';

/** Adds the store's id to our own API calls only, never to other hosts. */
export const merchantInterceptor: HttpInterceptorFn = (req, next) => {
  const id = inject(MerchantStore).merchantId();
  const ownApi = req.url === '/api' || req.url.startsWith('/api/');
  return next(id && ownApi ? req.clone({ setHeaders: { [MERCHANT_HEADER]: id } }) : req);
};
