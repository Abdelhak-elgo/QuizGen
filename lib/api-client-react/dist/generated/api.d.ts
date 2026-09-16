import type { QueryKey, UseMutationOptions, UseMutationResult, UseQueryOptions, UseQueryResult } from '@tanstack/react-query';
import type { Attempt, AttemptPage, AttemptSubmit, Document, DocumentPage, DocumentUpload, DocumentUploadResponse, DownloadUrl, HealthStatus, ListDocumentsParams, ListMyAttemptsParams, ListQuizzesParams, ListSessionsParams, ListUsersParams, Quiz, QuizAnalytics, QuizInput, QuizPage, QuizQuestionsUpdate, Session, SessionInput, SessionJoin, SessionPage, StudentDashboard, User, UserPage } from './api.schemas';
import { customFetch } from '../custom-fetch';
import type { ErrorType, BodyType } from '../custom-fetch';
type AwaitedInput<T> = PromiseLike<T> | T;
type Awaited<O> = O extends AwaitedInput<infer T> ? T : never;
type SecondParameter<T extends (...args: never) => unknown> = Parameters<T>[1];
export declare const getHealthCheckUrl: () => string;
/**
 * @summary Health check
 */
export declare const healthCheck: (options?: Parameters<typeof customFetch>[1]) => Promise<HealthStatus>;
export declare const getHealthCheckQueryKey: () => readonly ["/api/healthz"];
export declare const getHealthCheckQueryOptions: <TData = Awaited<ReturnType<typeof healthCheck>>, TError = ErrorType<unknown>>(options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof healthCheck>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof healthCheck>>, TError, TData> & {
    queryKey: QueryKey;
};
export type HealthCheckQueryResult = NonNullable<Awaited<ReturnType<typeof healthCheck>>>;
export type HealthCheckQueryError = ErrorType<unknown>;
/**
 * @summary Health check
 */
export declare function useHealthCheck<TData = Awaited<ReturnType<typeof healthCheck>>, TError = ErrorType<unknown>>(options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof healthCheck>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getSyncUserUrl: () => string;
/**
 * @summary Synchronise le profil Keycloak en base
 */
export declare const syncUser: (options?: Parameters<typeof customFetch>[1]) => Promise<User>;
export declare const getSyncUserMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof syncUser>>, TError, void, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof syncUser>>, TError, void, TContext>;
export type SyncUserMutationResult = NonNullable<Awaited<ReturnType<typeof syncUser>>>;
export type SyncUserMutationError = ErrorType<unknown>;
/**
* @summary Synchronise le profil Keycloak en base
*/
export declare const useSyncUser: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof syncUser>>, TError, void, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof syncUser>>, TError, void, TContext>;
export declare const getGetMeUrl: () => string;
/**
 * @summary Profil de l'utilisateur connecté
 */
export declare const getMe: (options?: Parameters<typeof customFetch>[1]) => Promise<User>;
export declare const getGetMeQueryKey: () => readonly ["/api/users/me"];
export declare const getGetMeQueryOptions: <TData = Awaited<ReturnType<typeof getMe>>, TError = ErrorType<unknown>>(options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getMe>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getMe>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetMeQueryResult = NonNullable<Awaited<ReturnType<typeof getMe>>>;
export type GetMeQueryError = ErrorType<unknown>;
/**
 * @summary Profil de l'utilisateur connecté
 */
export declare function useGetMe<TData = Awaited<ReturnType<typeof getMe>>, TError = ErrorType<unknown>>(options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getMe>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getListUsersUrl: (params?: ListUsersParams) => string;
/**
 * @summary Liste paginée des utilisateurs (ADMIN)
 */
export declare const listUsers: (params?: ListUsersParams, options?: Parameters<typeof customFetch>[1]) => Promise<UserPage>;
export declare const getListUsersQueryKey: (params?: ListUsersParams) => readonly ["/api/users", ...ListUsersParams[]];
export declare const getListUsersQueryOptions: <TData = Awaited<ReturnType<typeof listUsers>>, TError = ErrorType<unknown>>(params?: ListUsersParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listUsers>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof listUsers>>, TError, TData> & {
    queryKey: QueryKey;
};
export type ListUsersQueryResult = NonNullable<Awaited<ReturnType<typeof listUsers>>>;
export type ListUsersQueryError = ErrorType<unknown>;
/**
 * @summary Liste paginée des utilisateurs (ADMIN)
 */
export declare function useListUsers<TData = Awaited<ReturnType<typeof listUsers>>, TError = ErrorType<unknown>>(params?: ListUsersParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listUsers>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getListDocumentsUrl: (params?: ListDocumentsParams) => string;
/**
 * @summary Mes documents PDF
 */
export declare const listDocuments: (params?: ListDocumentsParams, options?: Parameters<typeof customFetch>[1]) => Promise<DocumentPage>;
export declare const getListDocumentsQueryKey: (params?: ListDocumentsParams) => readonly ["/api/documents", ...ListDocumentsParams[]];
export declare const getListDocumentsQueryOptions: <TData = Awaited<ReturnType<typeof listDocuments>>, TError = ErrorType<unknown>>(params?: ListDocumentsParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listDocuments>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof listDocuments>>, TError, TData> & {
    queryKey: QueryKey;
};
export type ListDocumentsQueryResult = NonNullable<Awaited<ReturnType<typeof listDocuments>>>;
export type ListDocumentsQueryError = ErrorType<unknown>;
/**
 * @summary Mes documents PDF
 */
export declare function useListDocuments<TData = Awaited<ReturnType<typeof listDocuments>>, TError = ErrorType<unknown>>(params?: ListDocumentsParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listDocuments>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getUploadDocumentUrl: () => string;
/**
 * @summary Upload un fichier PDF
 */
export declare const uploadDocument: (documentUpload: DocumentUpload, options?: Parameters<typeof customFetch>[1]) => Promise<DocumentUploadResponse>;
export declare const getUploadDocumentMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof uploadDocument>>, TError, {
        data: BodyType<DocumentUpload>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof uploadDocument>>, TError, {
    data: BodyType<DocumentUpload>;
}, TContext>;
export type UploadDocumentMutationResult = NonNullable<Awaited<ReturnType<typeof uploadDocument>>>;
export type UploadDocumentMutationBody = BodyType<DocumentUpload>;
export type UploadDocumentMutationError = ErrorType<unknown>;
/**
* @summary Upload un fichier PDF
*/
export declare const useUploadDocument: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof uploadDocument>>, TError, {
        data: BodyType<DocumentUpload>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof uploadDocument>>, TError, {
    data: BodyType<DocumentUpload>;
}, TContext>;
export declare const getGetDocumentUrl: (id: string) => string;
/**
 * @summary Détail d'un document
 */
export declare const getDocument: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<Document>;
export declare const getGetDocumentQueryKey: (id: string) => readonly [`/api/documents/${string}`];
export declare const getGetDocumentQueryOptions: <TData = Awaited<ReturnType<typeof getDocument>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getDocument>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getDocument>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetDocumentQueryResult = NonNullable<Awaited<ReturnType<typeof getDocument>>>;
export type GetDocumentQueryError = ErrorType<unknown>;
/**
 * @summary Détail d'un document
 */
export declare function useGetDocument<TData = Awaited<ReturnType<typeof getDocument>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getDocument>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getDeleteDocumentUrl: (id: string) => string;
/**
 * @summary Supprimer un document
 */
export declare const deleteDocument: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<void>;
export declare const getDeleteDocumentMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof deleteDocument>>, TError, {
        id: string;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof deleteDocument>>, TError, {
    id: string;
}, TContext>;
export type DeleteDocumentMutationResult = NonNullable<Awaited<ReturnType<typeof deleteDocument>>>;
export type DeleteDocumentMutationError = ErrorType<unknown>;
/**
* @summary Supprimer un document
*/
export declare const useDeleteDocument: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof deleteDocument>>, TError, {
        id: string;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof deleteDocument>>, TError, {
    id: string;
}, TContext>;
export declare const getGetDocumentDownloadUrlUrl: (id: string) => string;
/**
 * @summary URL présignée MinIO pour télécharger le PDF
 */
export declare const getDocumentDownloadUrl: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<DownloadUrl>;
export declare const getGetDocumentDownloadUrlQueryKey: (id: string) => readonly [`/api/documents/${string}/download-url`];
export declare const getGetDocumentDownloadUrlQueryOptions: <TData = Awaited<ReturnType<typeof getDocumentDownloadUrl>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getDocumentDownloadUrl>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getDocumentDownloadUrl>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetDocumentDownloadUrlQueryResult = NonNullable<Awaited<ReturnType<typeof getDocumentDownloadUrl>>>;
export type GetDocumentDownloadUrlQueryError = ErrorType<unknown>;
/**
 * @summary URL présignée MinIO pour télécharger le PDF
 */
export declare function useGetDocumentDownloadUrl<TData = Awaited<ReturnType<typeof getDocumentDownloadUrl>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getDocumentDownloadUrl>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getListQuizzesUrl: (params?: ListQuizzesParams) => string;
/**
 * @summary Mes quiz
 */
export declare const listQuizzes: (params?: ListQuizzesParams, options?: Parameters<typeof customFetch>[1]) => Promise<QuizPage>;
export declare const getListQuizzesQueryKey: (params?: ListQuizzesParams) => readonly ["/api/quizzes", ...ListQuizzesParams[]];
export declare const getListQuizzesQueryOptions: <TData = Awaited<ReturnType<typeof listQuizzes>>, TError = ErrorType<unknown>>(params?: ListQuizzesParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listQuizzes>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof listQuizzes>>, TError, TData> & {
    queryKey: QueryKey;
};
export type ListQuizzesQueryResult = NonNullable<Awaited<ReturnType<typeof listQuizzes>>>;
export type ListQuizzesQueryError = ErrorType<unknown>;
/**
 * @summary Mes quiz
 */
export declare function useListQuizzes<TData = Awaited<ReturnType<typeof listQuizzes>>, TError = ErrorType<unknown>>(params?: ListQuizzesParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listQuizzes>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getCreateQuizUrl: () => string;
/**
 * @summary Créer un quiz (déclenche la génération NLP)
 */
export declare const createQuiz: (quizInput: QuizInput, options?: Parameters<typeof customFetch>[1]) => Promise<Quiz>;
export declare const getCreateQuizMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof createQuiz>>, TError, {
        data: BodyType<QuizInput>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof createQuiz>>, TError, {
    data: BodyType<QuizInput>;
}, TContext>;
export type CreateQuizMutationResult = NonNullable<Awaited<ReturnType<typeof createQuiz>>>;
export type CreateQuizMutationBody = BodyType<QuizInput>;
export type CreateQuizMutationError = ErrorType<unknown>;
/**
* @summary Créer un quiz (déclenche la génération NLP)
*/
export declare const useCreateQuiz: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof createQuiz>>, TError, {
        data: BodyType<QuizInput>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof createQuiz>>, TError, {
    data: BodyType<QuizInput>;
}, TContext>;
export declare const getGetQuizUrl: (id: string) => string;
/**
 * @summary Détail d'un quiz avec ses questions
 */
export declare const getQuiz: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<Quiz>;
export declare const getGetQuizQueryKey: (id: string) => readonly [`/api/quizzes/${string}`];
export declare const getGetQuizQueryOptions: <TData = Awaited<ReturnType<typeof getQuiz>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getQuiz>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getQuiz>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetQuizQueryResult = NonNullable<Awaited<ReturnType<typeof getQuiz>>>;
export type GetQuizQueryError = ErrorType<unknown>;
/**
 * @summary Détail d'un quiz avec ses questions
 */
export declare function useGetQuiz<TData = Awaited<ReturnType<typeof getQuiz>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getQuiz>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getDeleteQuizUrl: (id: string) => string;
/**
 * @summary Supprimer un quiz
 */
export declare const deleteQuiz: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<void>;
export declare const getDeleteQuizMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof deleteQuiz>>, TError, {
        id: string;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof deleteQuiz>>, TError, {
    id: string;
}, TContext>;
export type DeleteQuizMutationResult = NonNullable<Awaited<ReturnType<typeof deleteQuiz>>>;
export type DeleteQuizMutationError = ErrorType<unknown>;
/**
* @summary Supprimer un quiz
*/
export declare const useDeleteQuiz: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof deleteQuiz>>, TError, {
        id: string;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof deleteQuiz>>, TError, {
    id: string;
}, TContext>;
export declare const getUpdateQuizQuestionsUrl: (id: string) => string;
/**
 * @summary Éditer les questions et publier le quiz
 */
export declare const updateQuizQuestions: (id: string, quizQuestionsUpdate: QuizQuestionsUpdate, options?: Parameters<typeof customFetch>[1]) => Promise<Quiz>;
export declare const getUpdateQuizQuestionsMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof updateQuizQuestions>>, TError, {
        id: string;
        data: BodyType<QuizQuestionsUpdate>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof updateQuizQuestions>>, TError, {
    id: string;
    data: BodyType<QuizQuestionsUpdate>;
}, TContext>;
export type UpdateQuizQuestionsMutationResult = NonNullable<Awaited<ReturnType<typeof updateQuizQuestions>>>;
export type UpdateQuizQuestionsMutationBody = BodyType<QuizQuestionsUpdate>;
export type UpdateQuizQuestionsMutationError = ErrorType<unknown>;
/**
* @summary Éditer les questions et publier le quiz
*/
export declare const useUpdateQuizQuestions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof updateQuizQuestions>>, TError, {
        id: string;
        data: BodyType<QuizQuestionsUpdate>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof updateQuizQuestions>>, TError, {
    id: string;
    data: BodyType<QuizQuestionsUpdate>;
}, TContext>;
export declare const getListSessionsUrl: (params?: ListSessionsParams) => string;
/**
 * @summary Mes sessions
 */
export declare const listSessions: (params?: ListSessionsParams, options?: Parameters<typeof customFetch>[1]) => Promise<SessionPage>;
export declare const getListSessionsQueryKey: (params?: ListSessionsParams) => readonly ["/api/sessions", ...ListSessionsParams[]];
export declare const getListSessionsQueryOptions: <TData = Awaited<ReturnType<typeof listSessions>>, TError = ErrorType<unknown>>(params?: ListSessionsParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listSessions>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof listSessions>>, TError, TData> & {
    queryKey: QueryKey;
};
export type ListSessionsQueryResult = NonNullable<Awaited<ReturnType<typeof listSessions>>>;
export type ListSessionsQueryError = ErrorType<unknown>;
/**
 * @summary Mes sessions
 */
export declare function useListSessions<TData = Awaited<ReturnType<typeof listSessions>>, TError = ErrorType<unknown>>(params?: ListSessionsParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listSessions>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getCreateSessionUrl: () => string;
/**
 * @summary Créer une session d'examen
 */
export declare const createSession: (sessionInput: SessionInput, options?: Parameters<typeof customFetch>[1]) => Promise<Session>;
export declare const getCreateSessionMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof createSession>>, TError, {
        data: BodyType<SessionInput>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof createSession>>, TError, {
    data: BodyType<SessionInput>;
}, TContext>;
export type CreateSessionMutationResult = NonNullable<Awaited<ReturnType<typeof createSession>>>;
export type CreateSessionMutationBody = BodyType<SessionInput>;
export type CreateSessionMutationError = ErrorType<unknown>;
/**
* @summary Créer une session d'examen
*/
export declare const useCreateSession: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof createSession>>, TError, {
        data: BodyType<SessionInput>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof createSession>>, TError, {
    data: BodyType<SessionInput>;
}, TContext>;
export declare const getGetSessionUrl: (id: string) => string;
/**
 * @summary Détail d'une session
 */
export declare const getSession: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<Session>;
export declare const getGetSessionQueryKey: (id: string) => readonly [`/api/sessions/${string}`];
export declare const getGetSessionQueryOptions: <TData = Awaited<ReturnType<typeof getSession>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getSession>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getSession>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetSessionQueryResult = NonNullable<Awaited<ReturnType<typeof getSession>>>;
export type GetSessionQueryError = ErrorType<unknown>;
/**
 * @summary Détail d'une session
 */
export declare function useGetSession<TData = Awaited<ReturnType<typeof getSession>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getSession>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getJoinSessionUrl: (id: string) => string;
/**
 * @summary Rejoindre une session (étudiant)
 */
export declare const joinSession: (id: string, sessionJoin?: SessionJoin, options?: Parameters<typeof customFetch>[1]) => Promise<Attempt>;
export declare const getJoinSessionMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof joinSession>>, TError, {
        id: string;
        data?: BodyType<SessionJoin>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof joinSession>>, TError, {
    id: string;
    data?: BodyType<SessionJoin>;
}, TContext>;
export type JoinSessionMutationResult = NonNullable<Awaited<ReturnType<typeof joinSession>>>;
export type JoinSessionMutationBody = BodyType<SessionJoin> | undefined;
export type JoinSessionMutationError = ErrorType<unknown>;
/**
* @summary Rejoindre une session (étudiant)
*/
export declare const useJoinSession: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof joinSession>>, TError, {
        id: string;
        data?: BodyType<SessionJoin>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof joinSession>>, TError, {
    id: string;
    data?: BodyType<SessionJoin>;
}, TContext>;
export declare const getCancelSessionUrl: (id: string) => string;
/**
 * @summary Annuler une session
 */
export declare const cancelSession: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<void>;
export declare const getCancelSessionMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof cancelSession>>, TError, {
        id: string;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof cancelSession>>, TError, {
    id: string;
}, TContext>;
export type CancelSessionMutationResult = NonNullable<Awaited<ReturnType<typeof cancelSession>>>;
export type CancelSessionMutationError = ErrorType<unknown>;
/**
* @summary Annuler une session
*/
export declare const useCancelSession: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof cancelSession>>, TError, {
        id: string;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof cancelSession>>, TError, {
    id: string;
}, TContext>;
export declare const getListMyAttemptsUrl: (params?: ListMyAttemptsParams) => string;
/**
 * @summary Historique de mes tentatives
 */
export declare const listMyAttempts: (params?: ListMyAttemptsParams, options?: Parameters<typeof customFetch>[1]) => Promise<AttemptPage>;
export declare const getListMyAttemptsQueryKey: (params?: ListMyAttemptsParams) => readonly ["/api/attempts/my", ...ListMyAttemptsParams[]];
export declare const getListMyAttemptsQueryOptions: <TData = Awaited<ReturnType<typeof listMyAttempts>>, TError = ErrorType<unknown>>(params?: ListMyAttemptsParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listMyAttempts>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof listMyAttempts>>, TError, TData> & {
    queryKey: QueryKey;
};
export type ListMyAttemptsQueryResult = NonNullable<Awaited<ReturnType<typeof listMyAttempts>>>;
export type ListMyAttemptsQueryError = ErrorType<unknown>;
/**
 * @summary Historique de mes tentatives
 */
export declare function useListMyAttempts<TData = Awaited<ReturnType<typeof listMyAttempts>>, TError = ErrorType<unknown>>(params?: ListMyAttemptsParams, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof listMyAttempts>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getGetAttemptUrl: (id: string) => string;
/**
 * @summary Détail d'une tentative
 */
export declare const getAttempt: (id: string, options?: Parameters<typeof customFetch>[1]) => Promise<Attempt>;
export declare const getGetAttemptQueryKey: (id: string) => readonly [`/api/attempts/${string}`];
export declare const getGetAttemptQueryOptions: <TData = Awaited<ReturnType<typeof getAttempt>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getAttempt>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getAttempt>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetAttemptQueryResult = NonNullable<Awaited<ReturnType<typeof getAttempt>>>;
export type GetAttemptQueryError = ErrorType<unknown>;
/**
 * @summary Détail d'une tentative
 */
export declare function useGetAttempt<TData = Awaited<ReturnType<typeof getAttempt>>, TError = ErrorType<unknown>>(id: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getAttempt>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getSubmitAttemptUrl: (id: string) => string;
/**
 * @summary Soumettre les réponses et obtenir le score
 */
export declare const submitAttempt: (id: string, attemptSubmit: AttemptSubmit, options?: Parameters<typeof customFetch>[1]) => Promise<Attempt>;
export declare const getSubmitAttemptMutationOptions: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof submitAttempt>>, TError, {
        id: string;
        data: BodyType<AttemptSubmit>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationOptions<Awaited<ReturnType<typeof submitAttempt>>, TError, {
    id: string;
    data: BodyType<AttemptSubmit>;
}, TContext>;
export type SubmitAttemptMutationResult = NonNullable<Awaited<ReturnType<typeof submitAttempt>>>;
export type SubmitAttemptMutationBody = BodyType<AttemptSubmit>;
export type SubmitAttemptMutationError = ErrorType<unknown>;
/**
* @summary Soumettre les réponses et obtenir le score
*/
export declare const useSubmitAttempt: <TError = ErrorType<unknown>, TContext = unknown>(options?: {
    mutation?: UseMutationOptions<Awaited<ReturnType<typeof submitAttempt>>, TError, {
        id: string;
        data: BodyType<AttemptSubmit>;
    }, TContext>;
    request?: SecondParameter<typeof customFetch>;
}) => UseMutationResult<Awaited<ReturnType<typeof submitAttempt>>, TError, {
    id: string;
    data: BodyType<AttemptSubmit>;
}, TContext>;
export declare const getGetQuizAnalyticsUrl: (quizId: string) => string;
/**
 * @summary Statistiques d'un quiz
 */
export declare const getQuizAnalytics: (quizId: string, options?: Parameters<typeof customFetch>[1]) => Promise<QuizAnalytics>;
export declare const getGetQuizAnalyticsQueryKey: (quizId: string) => readonly [`/api/analytics/quiz/${string}`];
export declare const getGetQuizAnalyticsQueryOptions: <TData = Awaited<ReturnType<typeof getQuizAnalytics>>, TError = ErrorType<unknown>>(quizId: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getQuizAnalytics>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getQuizAnalytics>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetQuizAnalyticsQueryResult = NonNullable<Awaited<ReturnType<typeof getQuizAnalytics>>>;
export type GetQuizAnalyticsQueryError = ErrorType<unknown>;
/**
 * @summary Statistiques d'un quiz
 */
export declare function useGetQuizAnalytics<TData = Awaited<ReturnType<typeof getQuizAnalytics>>, TError = ErrorType<unknown>>(quizId: string, options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getQuizAnalytics>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export declare const getGetStudentDashboardUrl: () => string;
/**
 * @summary Tableau de bord étudiant
 */
export declare const getStudentDashboard: (options?: Parameters<typeof customFetch>[1]) => Promise<StudentDashboard>;
export declare const getGetStudentDashboardQueryKey: () => readonly ["/api/analytics/student"];
export declare const getGetStudentDashboardQueryOptions: <TData = Awaited<ReturnType<typeof getStudentDashboard>>, TError = ErrorType<unknown>>(options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getStudentDashboard>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}) => UseQueryOptions<Awaited<ReturnType<typeof getStudentDashboard>>, TError, TData> & {
    queryKey: QueryKey;
};
export type GetStudentDashboardQueryResult = NonNullable<Awaited<ReturnType<typeof getStudentDashboard>>>;
export type GetStudentDashboardQueryError = ErrorType<unknown>;
/**
 * @summary Tableau de bord étudiant
 */
export declare function useGetStudentDashboard<TData = Awaited<ReturnType<typeof getStudentDashboard>>, TError = ErrorType<unknown>>(options?: {
    query?: UseQueryOptions<Awaited<ReturnType<typeof getStudentDashboard>>, TError, TData>;
    request?: SecondParameter<typeof customFetch>;
}): UseQueryResult<TData, TError> & {
    queryKey: QueryKey;
};
export {};
//# sourceMappingURL=api.d.ts.map