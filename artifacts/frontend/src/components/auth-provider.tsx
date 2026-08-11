import React, { createContext, useContext, useEffect, useState } from 'react';
import { keycloak } from '../lib/keycloak';
import { setAuthTokenGetter, setBaseUrl } from '@workspace/api-client-react';

export type UserRole = 'ENSEIGNANT' | 'ETUDIANT' | 'ADMIN' | null;

interface AuthContextType {
  user: any;
  role: UserRole;
  logout: () => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  role: null,
  logout: () => {},
  isLoading: true,
});

export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isInitialized, setIsInitialized] = useState(false);
  const [user, setUser] = useState<any>(null);
  const [role, setRole] = useState<UserRole>(null);

  useEffect(() => {
    // Check if running in browser
    if (typeof window === 'undefined') return;

    // Apply the base URL fix for the custom-fetch
    setBaseUrl('/api/v1');

    // Make sure we only init once
    keycloak
      .init({
        onLoad: 'login-required',
        checkLoginIframe: false,
        pkceMethod: 'S256',
      })
      .then((authenticated) => {
        if (authenticated) {
          setAuthTokenGetter(() => keycloak.token ?? null);
          
          keycloak.loadUserProfile().then((profile) => {
            setUser(profile);
            
            // Extract role from token
            let decodedRole: UserRole = null;
            if (keycloak.tokenParsed?.realm_access?.roles) {
              const roles = keycloak.tokenParsed.realm_access.roles;
              if (roles.includes('admin')) decodedRole = 'ADMIN';
              else if (roles.includes('enseignant')) decodedRole = 'ENSEIGNANT';
              else if (roles.includes('etudiant')) decodedRole = 'ETUDIANT';
            }
            // Fallback for role mapping if missing
            if (!decodedRole) decodedRole = 'ETUDIANT';
            setRole(decodedRole);
          });
        }
        setIsInitialized(true);
      })
      .catch((err) => {
        console.error('Failed to initialize Keycloak', err);
        setIsInitialized(true);
      });

    // Handle token refresh automatically
    const interval = setInterval(() => {
      if (keycloak.authenticated) {
        keycloak.updateToken(30).catch(() => {
          console.error('Failed to refresh token');
          keycloak.logout();
        });
      }
    }, 10000); // Check every 10 seconds

    return () => clearInterval(interval);
  }, []);

  const logout = () => {
    keycloak.logout();
  };

  return (
    <AuthContext.Provider value={{ user, role, logout, isLoading: !isInitialized }}>
      {children}
    </AuthContext.Provider>
  );
}
