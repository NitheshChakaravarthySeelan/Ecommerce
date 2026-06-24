const API_GATEWAY = 'http://localhost:3001';

export const api = {
  catalog: `${API_GATEWAY}/api/products`,
  cart: (userId: string) => `${API_GATEWAY}/api/cart/${userId}`,
  orders: `${API_GATEWAY}/api/orders`,
  payments: `${API_GATEWAY}/api/payments`,
  shipping: `${API_GATEWAY}/api/shipping`,
  auth: `${API_GATEWAY}/auth`,
  saga: `${API_GATEWAY}/api/saga`,
};
