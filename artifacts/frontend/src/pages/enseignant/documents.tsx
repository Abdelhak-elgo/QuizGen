import { useState, useRef } from 'react';
import { useListDocuments, useDeleteDocument, getListDocumentsQueryKey, useUploadDocument } from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';
import { EnseignantLayout } from '@/components/layout/enseignant-layout';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { FileText, Trash2, UploadCloud, BrainCircuit, Download, FilePlus2 } from 'lucide-react';
import { toast } from 'sonner';
import { Link } from 'wouter';

function formatBytes(bytes: number, decimals = 2) {
  if (!+bytes) return '0 Bytes';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

export default function DocumentLibrary() {
  const queryClient = useQueryClient();
  const { data: documentsData, isLoading } = useListDocuments({ size: 100 });
  const deleteDoc = useDeleteDocument();
  const uploadDoc = useUploadDocument();
  
  const [dragActive, setDragActive] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const documents = documentsData?.content || [];

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFile(e.dataTransfer.files[0]);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    if (e.target.files && e.target.files[0]) {
      handleFile(e.target.files[0]);
    }
  };

  const handleFile = async (file: File) => {
    if (file.type !== 'application/pdf') {
      toast.error('Format non supporté. Veuillez uploader un fichier PDF.');
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      toast.error('Fichier trop volumineux. La limite est de 20 MB.');
      return;
    }

    setUploading(true);
    const toastId = toast.loading('Upload en cours...');
    
    try {
      await uploadDoc.mutateAsync({ data: { file } });
      toast.success('Document uploadé avec succès !', { id: toastId });
      queryClient.invalidateQueries({ queryKey: getListDocumentsQueryKey() });
    } catch (error) {
      console.error(error);
      toast.error('Erreur lors de l\'upload du document.', { id: toastId });
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Êtes-vous sûr de vouloir supprimer ce document ?')) return;
    
    try {
      await deleteDoc.mutateAsync({ id });
      toast.success('Document supprimé.');
      queryClient.invalidateQueries({ queryKey: getListDocumentsQueryKey() });
    } catch (error) {
      toast.error('Erreur lors de la suppression.');
    }
  };

  return (
    <EnseignantLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Bibliothèque de documents</h1>
          <p className="text-muted-foreground mt-1">Gérez vos supports de cours PDF pour générer des quiz.</p>
        </div>

        {/* Upload Zone */}
        <div 
          className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors ${
            dragActive ? 'border-primary bg-primary/5' : 'border-border bg-card'
          }`}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
        >
          <div className="flex flex-col items-center justify-center space-y-4">
            <div className="bg-primary/10 p-4 rounded-full">
              <UploadCloud size={32} className="text-primary" />
            </div>
            <div className="space-y-1">
              <p className="text-sm font-medium">Glissez-déposez un fichier PDF ici</p>
              <p className="text-xs text-muted-foreground">ou cliquez pour parcourir (Max 20 MB)</p>
            </div>
            <input 
              ref={fileInputRef}
              type="file" 
              accept=".pdf" 
              className="hidden" 
              onChange={handleChange}
              disabled={uploading}
            />
            <Button 
              onClick={() => fileInputRef.current?.click()} 
              disabled={uploading}
              variant="outline"
            >
              {uploading ? 'Upload en cours...' : 'Sélectionner un fichier'}
            </Button>
          </div>
        </div>

        {/* Documents List */}
        <Card>
          <CardHeader>
            <CardTitle>Documents enregistrés</CardTitle>
            <CardDescription>Vos fichiers PDF prêts à être transformés en quiz</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="p-8 text-center text-muted-foreground">Chargement...</div>
            ) : documents.length === 0 ? (
              <div className="p-12 text-center text-muted-foreground flex flex-col items-center">
                <FilePlus2 size={48} className="text-muted/30 mb-4" />
                <p>Aucun document pour le moment.</p>
                <p className="text-sm mt-1">Uploadez votre premier support de cours ci-dessus.</p>
              </div>
            ) : (
              <div className="divide-y">
                {documents.map(doc => (
                  <div key={doc.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 hover:bg-slate-50 transition-colors gap-4">
                    <div className="flex items-start gap-4">
                      <div className="mt-1 bg-red-100 text-red-600 p-2 rounded-md">
                        <FileText size={20} />
                      </div>
                      <div className="space-y-1">
                        <p className="font-medium text-sm text-foreground">{doc.originalFilename}</p>
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                          <span>{doc.fileSize ? formatBytes(doc.fileSize) : 'Taille inconnue'}</span>
                          <span>•</span>
                          <span>{doc.pageCount ? `${doc.pageCount} pages` : 'Analyse en cours...'}</span>
                          <span>•</span>
                          <span>{doc.createdAt ? format(new Date(doc.createdAt), 'dd MMM yyyy à HH:mm', { locale: fr }) : ''}</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 self-end sm:self-auto">
                      {!doc.isProcessed && (
                        <Badge variant="secondary" className="mr-2 animate-pulse">En traitement</Badge>
                      )}
                      
                      <Button variant="outline" size="sm" asChild disabled={!doc.isProcessed}>
                        <Link href={`/enseignant/quizzes/new?documentId=${doc.id}`}>
                          <BrainCircuit size={14} className="mr-1.5" />
                          Générer
                        </Link>
                      </Button>
                      
                      <Button variant="ghost" size="icon" className="text-destructive hover:text-destructive hover:bg-destructive/10" onClick={() => handleDelete(doc.id)}>
                        <Trash2 size={16} />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </EnseignantLayout>
  );
}
