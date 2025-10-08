import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = 'http://localhost:8080/oauth2/token';

  private clientId = 'myclientid'; 
  private clientSecret = 'myclientsecret'; 

  constructor(private http: HttpClient) {}

  login(credentials: any): Observable<any> {

    const body = new HttpParams()
    .set('username', credentials.phone)
    .set('password', credentials.password)
    .set('grant_type', 'password');

    const base64Credentials = btoa(`${this.clientId}:${this.clientSecret}`);

    const headers = new HttpHeaders({'Content-Type' : 'application/x-www-form-urlencoded', 'Authorization': `Basic ${base64Credentials}`});
    
    return this.http.post(this.apiUrl, body.toString(), {headers});

  }
  
}
